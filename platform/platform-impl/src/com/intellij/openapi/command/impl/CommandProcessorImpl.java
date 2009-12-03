/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
public class CommandProcessorImpl extends CommandProcessorEx {
  private static class CommandDescriptor {
    public final Runnable myCommand;
    public final Project myProject;
    public String myName;
    public Object myGroupId;
    public final Document myDocument;
    public final UndoConfirmationPolicy myUndoConfirmationPolicy;

    public CommandDescriptor(Runnable command,
                             Project project,
                             String name,
                             Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy,
                             Document document) {
      myCommand = command;
      myProject = project;
      myName = name;
      myGroupId = groupId;
      myUndoConfirmationPolicy = undoConfirmationPolicy;
      myDocument = document;
    }
  }

  private CommandDescriptor myCurrentCommand = null;
  private final Stack<CommandDescriptor> myInterruptedCommands = new Stack<CommandDescriptor>();

//  private HashMap myStatisticsMap = new HashMap(); // command name --> count

  private final CopyOnWriteArrayList<CommandListener> myListeners = ContainerUtil.createEmptyCOWList();

  private int myUndoTransparentCount = 0;

  public void executeCommand(Runnable runnable, String name, Object groupId) {
    executeCommand(null, runnable, name, groupId);
  }

  public void executeCommand(Project project, Runnable runnable, String name, Object groupId) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT);
  }

  public void executeCommand(Project project,
                             final Runnable command,
                             final String name,
                             final Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, null);
  }

  public void executeCommand(Project project,
                             final Runnable command,
                             final String name,
                             final Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy,
                             Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (project != null && project.isDisposed()) return;

    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("executeCommand: " + command + ", name = " + name + ", groupId = " + groupId);
    }

    if (myCurrentCommand != null) {
      command.run();
      return;
    }
    Throwable throwable = null;
    try {
      myCurrentCommand = new CommandDescriptor(command, project, name, groupId, undoConfirmationPolicy, document);
      fireCommandStarted();
      command.run();
    }
    catch (Throwable th) {
      throwable = th;
    }
    finally {
      finishCommand(project, myCurrentCommand, throwable);
    }
  }

  @Nullable
  public Object startCommand(final Project project,
                             @Nls final String name,
                             final Object groupId,
                             final UndoConfirmationPolicy undoConfirmationPolicy) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (project != null && project.isDisposed()) return null;

    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("startCommand: name = " + name + ", groupId = " + groupId);
    }

    if (myCurrentCommand != null) {
      return null;
    }

    Document document = groupId instanceof Ref && ((Ref)groupId).get() instanceof Document ? (Document)((Ref)groupId).get() : null;
    myCurrentCommand = new CommandDescriptor(EmptyRunnable.INSTANCE, project, name, groupId, undoConfirmationPolicy, document);
    fireCommandStarted();
    return myCurrentCommand;
  }

  public void finishCommand(final Project project, final Object command, final Throwable throwable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandLog.LOG.assertTrue(myCurrentCommand != null, "no current command in progress");
    if (myCurrentCommand != command) return;
    final boolean failed;
    try {
      if (throwable instanceof AbnormalCommandTerminationException) {
        final AbnormalCommandTerminationException rollback = (AbnormalCommandTerminationException)throwable;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw new RuntimeException(rollback);
        }
        failed = true;
      }
      else if (throwable != null) {
        failed = true;
        if (throwable instanceof Error) {
          throw (Error)throwable;
        }
        else if (throwable instanceof RuntimeException) throw (RuntimeException)throwable;
        CommandLog.LOG.error(throwable);
      }
      else {
        failed = false;
      }
    }
    finally {
      fireCommandFinished();
    }
    if (failed) {
      if (project != null) {
        FileEditor editor = new FocusBasedCurrentEditorProvider().getCurrentEditor();
        final UndoManager undoManager = UndoManager.getInstance(project);
        if (undoManager.isUndoAvailable(editor)) {
          undoManager.undo(editor);
        }
      }
      Messages.showErrorDialog(project, "Cannot perform operation. Too complex, sorry.", "Failed to perform operation");
    }
  }

  private void fireCommandFinished() {
    CommandEvent event = new CommandEvent(this, myCurrentCommand.myCommand,
                                          myCurrentCommand.myName,
                                          myCurrentCommand.myGroupId,
                                          myCurrentCommand.myProject,
                                          myCurrentCommand.myUndoConfirmationPolicy,
                                          myCurrentCommand.myDocument);
    try {
      for (CommandListener listener : myListeners) {
        try {
          listener.beforeCommandFinished(event);
        }
        catch (Throwable e) {
          CommandLog.LOG.error(e);
        }
      }
    }
    finally {
      myCurrentCommand = null;
      for (CommandListener listener : myListeners) {
        try {
          listener.commandFinished(event);
        }
        catch (Throwable e) {
          CommandLog.LOG.error(e);
        }
      }
    }
  }

  public void enterModal() {
    myInterruptedCommands.push(myCurrentCommand);
    if (myCurrentCommand != null) {
      fireCommandFinished();
    }
  }

  public void leaveModal() {
    CommandLog.LOG.assertTrue(myCurrentCommand == null);
    myCurrentCommand = myInterruptedCommands.pop();
    if (myCurrentCommand != null) {
      fireCommandStarted();
    }
  }

  public void setCurrentCommandName(String name) {
    CommandLog.LOG.assertTrue(myCurrentCommand != null);
    myCurrentCommand.myName = name;
  }

  public void setCurrentCommandGroupId(Object groupId) {
    CommandLog.LOG.assertTrue(myCurrentCommand != null);
    myCurrentCommand.myGroupId = groupId;
  }

  @Nullable
  public Runnable getCurrentCommand() {
    return myCurrentCommand != null ? myCurrentCommand.myCommand : null;
  }

  @Nullable
  public String getCurrentCommandName() {
    if (myCurrentCommand != null) return myCurrentCommand.myName;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myName : null;
    }
    return null;
  }

  @Nullable
  public Object getCurrentCommandGroupId() {
    if (myCurrentCommand != null) return myCurrentCommand.myGroupId;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myGroupId : null;
    }
    return null;
  }

  @Nullable
  public Project getCurrentCommandProject() {
    return myCurrentCommand != null ? myCurrentCommand.myProject : null;
  }

  public void addCommandListener(CommandListener listener) {
    myListeners.add(listener);
  }

  public void addCommandListener(final CommandListener listener, Disposable parentDisposable) {
    addCommandListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeCommandListener(listener);
      }
    });
  }

  public void removeCommandListener(CommandListener listener) {
    myListeners.remove(listener);
  }

  public void runUndoTransparentAction(Runnable action) {
    if (myUndoTransparentCount == 0) fireUndoTransparentStarted();
    myUndoTransparentCount++;
    try {
      action.run();
    }
    finally {
      myUndoTransparentCount--;
      if (myUndoTransparentCount == 0) fireUndoTransparentFinished();
    }
  }

  public boolean isUndoTransparentActionInProgress() {
    return myUndoTransparentCount > 0;
  }

  public void markCurrentCommandAsGlobal(Project project) {
    UndoManager manager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    ((UndoManagerImpl)manager).markCurrentCommandAsGlobal();
  }

  private void fireCommandStarted() {
    CommandEvent event = new CommandEvent(this,
                                          myCurrentCommand.myCommand,
                                          myCurrentCommand.myName,
                                          myCurrentCommand.myGroupId,
                                          myCurrentCommand.myProject,
                                          myCurrentCommand.myUndoConfirmationPolicy,
                                          myCurrentCommand.myDocument);
    for (CommandListener listener : myListeners) {
      try {
        listener.commandStarted(event);
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentStarted() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionStarted();
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentFinished() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionFinished();
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }
}

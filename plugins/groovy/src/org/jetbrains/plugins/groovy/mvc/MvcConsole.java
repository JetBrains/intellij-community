/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.DisposeAwareRunnable;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MvcConsole implements Disposable {

  private static final Key<Boolean> UPDATING_BY_CONSOLE_PROCESS = Key.create("UPDATING_BY_CONSOLE_PROCESS");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcConsole");
  @NonNls private static final String CONSOLE_ID = "Groovy MVC Console";

  @NonNls public static final String TOOL_WINDOW_ID = "Console";
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("Mvc notifications", TOOL_WINDOW_ID);

  private final ConsoleViewImpl myConsole;
  private final Project myProject;
  private final ToolWindow myToolWindow;
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final Queue<MyProcessInConsole> myProcessQueue = new LinkedList<>();



  private final MyKillProcessAction myKillAction = new MyKillProcessAction();
  private boolean myExecuting = false;
  private final Content myContent;

  public MvcConsole(Project project, TextConsoleBuilderFactory consoleBuilderFactory) {
    myProject = project;
    myConsole = (ConsoleViewImpl)consoleBuilderFactory.createBuilder(myProject).getConsole();
    Disposer.register(this, myConsole);

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.BOTTOM, this, true);
    myToolWindow.setIcon(JetgroovyIcons.Groovy.Groovy_13x13);

    myContent = setUpToolWindow();
  }

  public static MvcConsole getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MvcConsole.class);
  }

  public static boolean isUpdatingVfsByConsoleProcess(@NotNull Module module) {
    Boolean flag = module.getUserData(UPDATING_BY_CONSOLE_PROCESS);
    return flag != null && flag;
  }

  private Content setUpToolWindow() {
    //Create runner UI layout
    final RunnerLayoutUi.Factory factory = RunnerLayoutUi.Factory.getInstance(myProject);
    final RunnerLayoutUi layoutUi = factory.create("", "", "session", myProject);

    // Adding actions
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(myKillAction);
    group.addSeparator();

    layoutUi.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);

    final Content console = layoutUi.createContent(CONSOLE_ID, myConsole.getComponent(), "", null, null);
    layoutUi.addContent(console, 0, PlaceInGrid.right, false);

    final JComponent uiComponent = layoutUi.getComponent();
    myPanel.add(uiComponent, BorderLayout.CENTER);

    final ContentManager manager = myToolWindow.getContentManager();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(uiComponent, null, true);
    manager.addContent(content);
    return content;
  }

  public void show(@Nullable final Runnable runnable, boolean focus) {
    Runnable r = null;
    if (runnable != null) {
      r = DisposeAwareRunnable.create(runnable, myProject);
    }

    myToolWindow.activate(r, focus);
  }

  private static class MyProcessInConsole implements ConsoleProcessDescriptor {
    final Module module;
    final GeneralCommandLine commandLine;
    @Nullable final Runnable onDone;
    final boolean closeOnDone;
    final boolean showConsole;
    final String[] input;
    private final List<ProcessListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    private OSProcessHandler myHandler;

    public MyProcessInConsole(final Module module,
                              final GeneralCommandLine commandLine,
                              @Nullable final Runnable onDone,
                              final boolean showConsole,
                              final boolean closeOnDone,
                              final String[] input) {
      this.module = module;
      this.commandLine = commandLine;
      this.onDone = onDone;
      this.closeOnDone = closeOnDone;
      this.input = input;
      this.showConsole = showConsole;
    }

    @Override
    public ConsoleProcessDescriptor addProcessListener(@NotNull ProcessListener listener) {
      if (myHandler != null) {
        myHandler.addProcessListener(listener);
      }
      else {
        myListeners.add(listener);
      }
      return this;
    }

    @Override
    public ConsoleProcessDescriptor waitWith(ProgressIndicator progressIndicator) {
      if (myHandler != null) {
        doWait(progressIndicator);
      }
      return this;
    }

    private void doWait(ProgressIndicator progressIndicator) {
      while (!myHandler.waitFor(500)) {
        if (progressIndicator.isCanceled()) {
          myHandler.destroyProcess();
          break;
        }
      }
    }

    public void setHandler(OSProcessHandler handler) {
      myHandler = handler;
      for (final ProcessListener listener : myListeners) {
        handler.addProcessListener(listener);
      }
    }
  }

  public static ConsoleProcessDescriptor executeProcess(final Module module,
                                                        final GeneralCommandLine commandLine,
                                                        @Nullable final Runnable onDone,
                                                        final boolean closeOnDone,
                                                        final String... input) {
    return getInstance(module.getProject()).executeProcess(module, commandLine, onDone, true, closeOnDone, input);
  }

  public ConsoleProcessDescriptor executeProcess(final Module module,
                                                 final GeneralCommandLine commandLine,
                                                 @Nullable final Runnable onDone,
                                                 boolean showConsole,
                                                 final boolean closeOnDone,
                                                 final String... input) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert module.getProject() == myProject;
    
    final MyProcessInConsole process = new MyProcessInConsole(module, commandLine, onDone, showConsole, closeOnDone, input);
    if (isExecuting()) {
      myProcessQueue.add(process);
    }
    else {
      executeProcessImpl(process, true);
    }
    return process;
  }

  public boolean isExecuting() {
    return myExecuting;
  }

  private void executeProcessImpl(final MyProcessInConsole pic, boolean toFocus) {
    final Module module = pic.module;
    final GeneralCommandLine commandLine = pic.commandLine;
    final String[] input = pic.input;
    final boolean closeOnDone = pic.closeOnDone;
    final Runnable onDone = pic.onDone;

    assert module.getProject() == myProject;

    myExecuting = true;

    // Module creation was cancelled
    if (module.isDisposed()) return;

    final ModalityState modalityState = ModalityState.current();
    final boolean modalContext = modalityState != ModalityState.NON_MODAL;

    if (!modalContext && pic.showConsole) {
      show(null, toFocus);
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    myConsole.print(commandLine.getCommandLineString(), ConsoleViewContentType.SYSTEM_OUTPUT);
    final OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(commandLine);

      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      OutputStreamWriter writer = new OutputStreamWriter(handler.getProcess().getOutputStream());
      for (String s : input) {
        writer.write(s);
      }
      writer.flush();

      final Ref<Boolean> gotError = new Ref<>(false);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key key) {
          if (key == ProcessOutputTypes.STDERR) gotError.set(true);
          LOG.debug("got text: " + event.getText());
        }

        @Override
        public void processTerminated(ProcessEvent event) {
          final int exitCode = event.getExitCode();
          if (exitCode == 0 && !gotError.get().booleanValue()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              if (myProject.isDisposed() || !closeOnDone) return;
              myToolWindow.hide(null);
            }, modalityState);
          }
        }
      });
    }
    catch (final Exception e) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Messages.showErrorDialog(e.getMessage(), "Cannot Start Process");

        try {
          if (onDone != null && !module.isDisposed()) onDone.run();
        }
        catch (Exception e1) {
          LOG.error(e1);
        }
      }, modalityState);
      return;
    }

    pic.setHandler(handler);
    myKillAction.setHandler(handler);

    final MvcFramework framework = MvcFramework.getInstance(module);
    myToolWindow.setIcon(framework == null ? JetgroovyIcons.Groovy.Groovy_13x13 : framework.getToolWindowIcon());

    myContent.setDisplayName((framework == null ? "" : framework.getDisplayName() + ":") + "Executing...");
    myConsole.scrollToEnd();
    myConsole.attachToProcess(handler);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      handler.startNotify();
      handler.waitFor();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) return;

        module.putUserData(UPDATING_BY_CONSOLE_PROCESS, true);
        LocalFileSystem.getInstance().refresh(false);
        module.putUserData(UPDATING_BY_CONSOLE_PROCESS, null);

        try {
          if (onDone != null && !module.isDisposed()) onDone.run();
        }
        catch (Exception e) {
          LOG.error(e);
        }
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
        myKillAction.setHandler(null);
        myContent.setDisplayName("");

        myExecuting = false;

        final MyProcessInConsole pic1 = myProcessQueue.poll();
        if (pic1 != null) {
          executeProcessImpl(pic1, false);
        }
      }, modalityState);
    });
  }

  @Override
  public void dispose() {
  }

  private class MyKillProcessAction extends AnAction {
    private OSProcessHandler myHandler = null;

    public MyKillProcessAction() {
      super("Kill process", "Kill process", AllIcons.Debugger.KillProcess);
    }

    public void setHandler(@Nullable OSProcessHandler handler) {
      myHandler = handler;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      if (myHandler != null) {
        final Process process = myHandler.getProcess();
        process.destroy();
        myConsole.print("Process terminated", ConsoleViewContentType.ERROR_OUTPUT);
      }
    }

    public boolean isEnabled() {
      return myHandler != null;
    }
  }

  public ConsoleViewImpl getConsole() {
    return myConsole;
  }
}

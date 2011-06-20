/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MvcConsole implements Disposable {

  private static final Key<Boolean> UPDATING_BY_CONSOLE_PROCESS = Key.create("UPDATING_BY_CONSOLE_PROCESS");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcConsole");
  private final ConsoleViewImpl myConsole;
  private final Project myProject;
  private final ToolWindow myToolWindow;
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final Queue<MyProcessInConsole> myProcessQueue = new LinkedList<MyProcessInConsole>();

  @NonNls private static final String CONSOLE_ID = "Groovy MVC Console";

  @NonNls public static final String TOOL_WINDOW_ID = "Console";

  private final MyKillProcessAction myKillAction = new MyKillProcessAction();
  private volatile boolean myExecuting = false;
  private final Content myContent;
  private static final Icon KILL_PROCESS_ICON = IconLoader.getIcon("/debugger/killProcess.png");

  public MvcConsole(Project project, TextConsoleBuilderFactory consoleBuilderFactory) {
    myProject = project;
    myConsole = (ConsoleViewImpl)consoleBuilderFactory.createBuilder(myProject).getConsole();
    myConsole.setModalityStateForUpdate(new Computable<ModalityState>() {
      @Nullable
      public ModalityState compute() {
        return null;
      }
    });
    Disposer.register(this, myConsole);

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.BOTTOM, this, true);
    myToolWindow.setIcon(GroovyIcons.GROOVY_ICON_16x16);

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
    myToolWindow.activate(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;

        if (runnable != null) {
          runnable.run();
        }
      }
    }, focus);
  }

  private static class MyProcessInConsole implements ConsoleProcessDescriptor {
    final Module module;
    final ProcessBuilder pb;
    final @Nullable Runnable onDone;
    final boolean closeOnDone;
    final String[] input;
    private ProgressIndicator myIndicator;
    private Runnable myAfter;
    private final List<ProcessListener> myListeners = new SmartList<ProcessListener>();

    private OSProcessHandler myHandler;

    public MyProcessInConsole(final Module module,
                              final ProcessBuilder pb,
                              final Runnable onDone,
                              final boolean closeOnDone,
                              final String[] input) {
      this.module = module;
      this.pb = pb;
      this.onDone = onDone;
      this.closeOnDone = closeOnDone;
      this.input = input;
    }

    public ConsoleProcessDescriptor addProcessListener(@NotNull ProcessListener listener) {
      if (myHandler != null) {
        myHandler.addProcessListener(listener);
      } else {
        myListeners.add(listener);
      }
      return this;
    }

    public ConsoleProcessDescriptor waitWith(ProgressIndicator progressIndicator, @Nullable Runnable after) {
      if (myHandler != null) {
        doWait(progressIndicator, after);
      } else {
        myIndicator = progressIndicator;
        myAfter = after;
      }
      return this;
    }

    private void doWait(ProgressIndicator progressIndicator, @Nullable Runnable after) {
      while (!myHandler.waitFor(500)) {
        if (progressIndicator.isCanceled()) {
          myHandler.destroyProcess();
          break;
        }
      }
      if (after != null) {
        after.run();
      }
    }

    public void setHandler(OSProcessHandler handler) {
      myHandler = handler;
      for (final ProcessListener listener : myListeners) {
        handler.addProcessListener(listener);
      }
      if (myIndicator != null) {
        doWait(myIndicator, myAfter);
      }
    }
  }

  public ConsoleProcessDescriptor executeProcess(final Module module,
                                          final ProcessBuilder pb,
                                          final @Nullable Runnable onDone,
                                          final boolean closeOnDone,
                                          final String... input) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final MyProcessInConsole process = new MyProcessInConsole(module, pb, onDone, closeOnDone, input);
    if (isExecuting()) {
      myProcessQueue.add(process);
    } else {
      executeProcessImpl(process, true);
    }
    return process;
  }

  private boolean isExecuting() {
    return myExecuting;
  }

  private void executeProcessImpl(final MyProcessInConsole pic, boolean toFocus) {
    final Module module = pic.module;
    final ProcessBuilder pb = pic.pb;
    final String[] input = pic.input;
    final boolean closeOnDone = pic.closeOnDone;
    final Runnable onDone = pic.onDone;

    assert module.getProject() == myProject;

    myExecuting = true;

    final ModalityState modalityState = ModalityState.current();
    final boolean modalContext = modalityState != ModalityState.NON_MODAL;

    final Runnable runnable = new Runnable() {
      public void run() {
        // Module creation was cancelled
        if (module.isDisposed()) return;

        FileDocumentManager.getInstance().saveAllDocuments();
        myConsole.print(StringUtil.join(pb.command(), " "), ConsoleViewContentType.SYSTEM_OUTPUT);
        final OSProcessHandler handler;
        OutputStreamWriter writer;
        try {
          if (pb.command() == null || pb.command().size() == 0) return;

          Process process = pb.start();
          handler = new OSProcessHandler(process, "");

          writer = new OutputStreamWriter(process.getOutputStream());
          for (String s : input) {
            writer.write(s);
          }
          writer.flush();

          final Ref<Boolean> gotError = new Ref<Boolean>(false);
          handler.addProcessListener(new ProcessAdapter() {
            public void onTextAvailable(ProcessEvent event, Key key) {
              if (key == ProcessOutputTypes.STDERR) gotError.set(true);
              LOG.debug("got text: " + event.getText());
            }

            public void processTerminated(ProcessEvent event) {
              final int exitCode = event.getExitCode();
              if (exitCode == 0 && !gotError.get().booleanValue()) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (myProject.isDisposed() || !closeOnDone) return;
                    myToolWindow.hide(null);
                  }
                }, modalityState);
              }
            }
          });
        }
        catch (final IOException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(e.getMessage(), "Cannot start process");

              try {
                if (onDone != null && !module.isDisposed()) onDone.run();
              }
              catch (Exception e) {
                LOG.error(e);
              }
            }
          }, modalityState);
          return;
        }

        pic.setHandler(handler);
        myKillAction.setHandler(handler);

        final MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(module);

        myContent.setDisplayName((framework == null ? "" : framework.getDisplayName() + ":") + "Executing...");
        myConsole.scrollToEnd();
        myConsole.attachToProcess(handler);
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            handler.startNotify();
            handler.waitFor();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
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

                final MyProcessInConsole pic = myProcessQueue.poll();
                if (pic != null) {
                  executeProcessImpl(pic, false);
                }
              }
            }, modalityState);
          }
        });
      }
    };
    
    if (modalContext) {
      runnable.run();
    } else {
      show(runnable, toFocus);
    }
  }

  public void dispose() {
  }

  private class MyKillProcessAction extends AnAction {
    private OSProcessHandler myHandler = null;

    public MyKillProcessAction() {
      super("Kill process", "Kill process", KILL_PROCESS_ICON);
    }

    public void setHandler(OSProcessHandler handler) {
      myHandler = handler;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
    }

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

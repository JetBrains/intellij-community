/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.console.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import java.util.*;

/**
 * Created by Max Medvedev on 9/20/13
 */
public abstract class GroovyShellActionBase extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GroovyShellActionBase.class);

  public static final String GROOVY_SHELL_EXECUTE = "Groovy.Shell.Execute";
  public static final Key<Boolean> GROOVY_SHELL_FILE = Key.create("GROOVY_SHELL_FILE");
  private static final String GROOVY_SHELL_LAST_MODULE = "Groovy.Shell.LastModule";

  private List<Module> getGroovyCompatibleModules(Project project) {
    ArrayList<Module> result = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (isSuitableModule(module)) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
          result.add(module);
        }
      }
    }
    return result;
  }

  protected boolean isSuitableModule(Module module) {
    return GroovyUtils.isSuitableModule(module);
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    boolean enabled = project != null && !getGroovyCompatibleModules(project).isEmpty();

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;

    selectModule(project, new Consumer<Module>() {
      @Override
      public void consume(final Module module) {
        doRunShell(module);
      }
    });
  }

  private void selectModule(Project project, final Consumer<Module> callback) {
    List<Module> modules = new ArrayList<Module>();
    final Map<Module, String> versions = new HashMap<Module, String>();

    for (Module module : getGroovyCompatibleModules(project)) {
      GroovyShellRunner runner = getRunner(module);
      if (runner != null) {
        modules.add(module);
        versions.put(module, runner.getTitle(module));
      }
    }

    if (modules.size() == 1) {
      callback.consume(modules.get(0));
      return;
    }

    Collections.sort(modules, ModulesAlphaComparator.INSTANCE);

    BaseListPopupStep<Module> step =
      new BaseListPopupStep<Module>("Which module to use classpath of?", modules, PlatformIcons.CONTENT_ROOT_ICON_CLOSED) {
        @NotNull
        @Override
        public String getTextFor(Module value) {
          return value.getName() + versions.get(value);
        }

        @Override
        public String getIndexedString(Module value) {
          return value.getName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Module selectedValue, boolean finalChoice) {
          PropertiesComponent.getInstance(selectedValue.getProject()).setValue(GROOVY_SHELL_LAST_MODULE, selectedValue.getName());
          callback.consume(selectedValue);
          return null;
        }
      };

    final String lastModuleName = PropertiesComponent.getInstance(project).getValue(GROOVY_SHELL_LAST_MODULE);
    if (lastModuleName != null) {
      int defaultOption = ContainerUtil.indexOf(modules, new Condition<Module>() {
        @Override
        public boolean value(Module module) {
          return module.getName().equals(lastModuleName);
        }
      });
      if (defaultOption >= 0) {
        step.setDefaultOptionIndex(defaultOption);
      }
    }

    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project);
  }

  protected void doRunShell(final Module module) {
    final GroovyShellRunner shellRunner = getRunner(module);
    if (shellRunner == null) return;

    AbstractConsoleRunnerWithHistory<LanguageConsoleView> runner = new GroovyConsoleRunner(getTitle(), shellRunner, module);
    try {
      runner.initAndRun();
    }
    catch (ExecutionException e) {
      LOG.info(e);
      Messages.showErrorDialog(module.getProject(), e.getMessage(), "Cannot Run " + getTitle());
    }
  }

  protected abstract GroovyShellRunner getRunner(Module module);

  public abstract String getTitle();

  protected abstract LanguageConsoleImpl createConsole(Project project, String title);

  private class GroovyConsoleRunner extends AbstractConsoleRunnerWithHistory<LanguageConsoleView> {
    private GroovyShellRunner myShellRunner;
    private Module myModule;

    private GroovyConsoleRunner(@NotNull String consoleTitle,
                                @NotNull GroovyShellRunner shellRunner,
                                @NotNull Module module) {
      super(module.getProject(), consoleTitle, shellRunner.getWorkingDirectory(module));
      myShellRunner = shellRunner;
      myModule = module;
    }

    @Override
    protected List<AnAction> fillToolBarActions(DefaultActionGroup toolbarActions,
                                                final Executor defaultExecutor,
                                                final RunContentDescriptor contentDescriptor) {
      BuildAndRestartConsoleAction rebuildAction = new BuildAndRestartConsoleAction(myModule, getProject(), defaultExecutor, contentDescriptor, GroovyShellActionBase.this);
      toolbarActions.add(rebuildAction);
      List<AnAction> actions = super.fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);
      actions.add(rebuildAction);
      Disposer.register(getConsoleView(), rebuildAction);
      return actions;
    }

    @Override
    protected LanguageConsoleView createConsoleView() {
      LanguageConsoleViewImpl res = new LanguageConsoleViewImpl(createConsole(getProject(), getConsoleTitle()));
      GroovyFileImpl file = (GroovyFileImpl)res.getConsole().getFile();
      assert file.getContext() == null;
      file.putUserData(GROOVY_SHELL_FILE, Boolean.TRUE);

      file.setContext(myShellRunner.getContext(myModule));

      return res;
    }

    @Override
    protected Process createProcess() throws ExecutionException {
      JavaParameters javaParameters = myShellRunner.createJavaParameters(myModule);

      final Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
      assert sdk != null;
      SdkTypeId sdkType = sdk.getSdkType();
      assert sdkType instanceof JavaSdkType;
      final String exePath = ((JavaSdkType)sdkType).getVMExecutablePath(sdk);

      return JdkUtil.setupJVMCommandLine(exePath, javaParameters, true).createProcess();
    }

    @Override
    protected OSProcessHandler createProcessHandler(Process process) {
      return new OSProcessHandler(process);
    }

    @NotNull
    @Override
    protected ProcessBackedConsoleExecuteActionHandler createExecuteActionHandler() {
      ProcessBackedConsoleExecuteActionHandler handler = new ProcessBackedConsoleExecuteActionHandler(getProcessHandler(), false) {
        @Override
        public void processLine(@NotNull String line) {
          super.processLine(myShellRunner.transformUserInput(line));
        }

        @Override
        public String getEmptyExecuteAction() {
          return GROOVY_SHELL_EXECUTE;
        }
      };
      new ConsoleHistoryController(getConsoleTitle(), null, getLanguageConsole(), handler.getConsoleHistoryModel()).install();
      return handler;
    }
  }
}

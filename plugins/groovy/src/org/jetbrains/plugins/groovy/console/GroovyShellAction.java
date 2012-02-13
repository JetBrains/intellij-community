/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.runners.ConsoleExecuteActionHandler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathsList;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GroovyShellAction extends DumbAwareAction {

  private static List<Module> getModulesWithGroovySupport(Project project) {
    ArrayList<Module> result = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (LibrariesUtil.getGroovyHomePath(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null && !getModulesWithGroovySupport(project).isEmpty()) {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      return;
    }

    e.getPresentation().setEnabled(false);
    e.getPresentation().setVisible(false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;
    List<Module> modules = getModulesWithGroovySupport(project);
    if (modules.size() == 1) {
      runShell(modules.get(0));
      return;
    }

    BaseListPopupStep<Module> step =
      new BaseListPopupStep<Module>("Which module classpath to use?", modules, PlatformIcons.CONTENT_ROOT_ICON_CLOSED) {
        @NotNull
        @Override
        public String getTextFor(Module value) {
          return value.getName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Module selectedValue, boolean finalChoice) {
          PropertiesComponent.getInstance(selectedValue.getProject()).setValue("Groovy.Shell.LastModule", selectedValue.getName());
          runShell(selectedValue);
          return null;
        }
      };
    for (int i = 0; i < modules.size(); i++) {
      Module module = modules.get(i);
      if (module.getName().equals(PropertiesComponent.getInstance(project).getValue("Groovy.Shell.LastModule"))) {
        step.setDefaultOptionIndex(i);
        break;
      }
    }
    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(project);
  }

  private static void runShell(final Module module) {
    AbstractConsoleRunnerWithHistory<GroovyConsoleView> runner =
      new AbstractConsoleRunnerWithHistory<GroovyConsoleView>(module.getProject(), "Groovy Shell", new CommandLineArgumentsProvider(), null) {

        @Override
        protected GroovyConsoleView createConsoleView() {
          return new GroovyConsoleView(getProject());
        }

        @Override
        protected Process createProcess(CommandLineArgumentsProvider provider) throws ExecutionException {
          final JavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
          DefaultGroovyScriptRunner.configureGenericGroovyRunner(javaParameters, module, "groovy.ui.GroovyMain");
          PathsList list = GroovyScriptRunner.getClassPathFromRootModel(module, true, javaParameters, true);
          if (list != null) {
            javaParameters.getProgramParametersList().addAll("--classpath", list.getPathsString());
          }
          javaParameters.getProgramParametersList().addAll("-p", GroovyScriptRunner.getPathInConf("console.txt"));

          final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          assert sdk != null;
          SdkType sdkType = sdk.getSdkType();
          assert sdkType instanceof JavaSdkType;
          final String exePath = ((JavaSdkType)sdkType).getVMExecutablePath(sdk);

          return JdkUtil.setupJVMCommandLine(exePath, javaParameters, true).createProcess();
        }

        @Override
        protected OSProcessHandler createProcessHandler(Process process, String commandLine) {
          return new OSProcessHandler(process, commandLine);
        }

        @NotNull
        @Override
        protected ConsoleExecuteActionHandler createConsoleExecuteActionHandler() {
          ConsoleExecuteActionHandler handler = new ConsoleExecuteActionHandler(getProcessHandler(), false) {
            @Override
            public void processLine(String line) {
              super.processLine(StringUtil.replace(line, "\n", "###\\n"));
            }
          };
          new ConsoleHistoryController("Groovy Shell", null, getLanguageConsole(), handler.getConsoleHistoryModel()).install();
          return handler;
        }
      };
    try {
      runner.initAndRun();
    }
    catch (ExecutionException e1) {
      throw new RuntimeException(e1);
    }
  }

  private static class GroovyConsoleView extends LanguageConsoleViewImpl {
    protected GroovyConsoleView(final Project project) {
      super(project, new LanguageConsoleImpl(project, "Groovy Console", GroovyFileType.GROOVY_LANGUAGE));
    }
  }
}

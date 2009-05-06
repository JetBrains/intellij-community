package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public class AppEngineUploader {
  private Project myProject;

  public AppEngineUploader(Project project) {
    myProject = project;
  }

  public void startUploading(@NotNull AppEngineFacet facet) {
    final WebFacet webFacet = facet.getWebFacet();
    final BuildConfiguration buildProperties = webFacet.getBuildConfiguration().getBuildProperties();
    final String explodedPath = buildProperties.getExplodedPath();
    if (!buildProperties.isExplodedEnabled() || explodedPath == null) {
      Messages.showErrorDialog(myProject, "Exploded directory isn't specified for '" + webFacet.getName() + "' facet (in module '" + webFacet.getModule().getName() + "')",
                               CommonBundle.getErrorTitle());
      return;
    }

    final AppEngineSdk sdk = facet.getSdk();
    if (!sdk.getAppCfgFile().exists()) {
      Messages.showErrorDialog(myProject, "Path to App Engine SDK isn't specified correctly in App Engine Facet settings", CommonBundle.getErrorTitle());
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Uploading application", true, null) {
      public void run(@NotNull ProgressIndicator indicator) {
        compileAndUpload(webFacet, sdk, explodedPath);
      }
    });
  }

  private void compileAndUpload(WebFacet webFacet, final AppEngineSdk sdk, final String explodedPath) {
    final Runnable startUploading = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            startUploadingProcess(sdk, explodedPath);
          }
        });
      }
    };

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final Module module = webFacet.getModule();
    final CompileScope compileScope = compilerManager.createModuleCompileScope(module, true);
    if (!compilerManager.isUpToDate(compileScope)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final int answer = Messages.showYesNoDialog(myProject, "Exploded directory may be out of date. Do you want to build it?", "Upload Application", null);
          if (answer == 0) {
            compilerManager.make(compileScope, new CompileStatusNotification() {
              public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
                if (!aborted && errors == 0) {
                  startUploading.run();
                }
              }
            });
          }
          else {
            startUploading.run();
          }
        }
      });
    }
    else {
      startUploading.run();
    }
  }

  private void startUploadingProcess(AppEngineSdk sdk, String explodedPath) {
    final ProcessBuilder processBuilder = new ProcessBuilder().command(sdk.getAppCfgFile().getAbsolutePath(), "update",
                                                                       FileUtil.toSystemDependentName(explodedPath));
    final String commandLine = StringUtil.join(processBuilder.command(), " ");
    final Process process;
    try {
      process = processBuilder.start();
    }
    catch (final IOException e) {
      Messages.showErrorDialog(myProject, "Cannot start 'appcfg' script: " + e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
    final RunnerLayoutUi ui = RunnerLayoutUi.Factory.getInstance(myProject).create("Upload", "Upload Application", "Upload Application", myProject);
    final DefaultActionGroup group = new DefaultActionGroup();
    ui.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);
    ui.addContent(ui.createContent("upload", console.getComponent(), "Upload Application", null, console.getPreferredFocusableComponent()));

    ProcessHandler processHandler = new OSProcessHandler(process, commandLine);
    console.attachToProcess(processHandler);
    final RunContentDescriptor contentDescriptor = new RunContentDescriptor(console, processHandler, ui.getComponent(), "Upload Application");
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
    group.add(new CloseAction(executor, contentDescriptor, myProject));

    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(executor, contentDescriptor);
    processHandler.startNotify();
  }
}

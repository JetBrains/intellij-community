/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.cloud.AppEngineAuthData;
import com.intellij.appengine.cloud.AppEngineServerConfiguration;
import com.intellij.appengine.descriptor.dom.AppEngineWebApp;
import com.intellij.appengine.facet.AppEngineAccountDialog;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;

/**
 * @author nik
 */
public class AppEngineUploader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.actions.AppEngineUploader");
  private final Project myProject;
  private final Artifact myArtifact;
  private final AppEngineFacet myAppEngineFacet;
  private final AppEngineSdk mySdk;
  private final AppEngineAuthData myAuthData;
  private final ServerRuntimeInstance.DeploymentOperationCallback myCallback;
  private final LoggingHandler myLoggingHandler;

  private AppEngineUploader(Project project, Artifact artifact, AppEngineFacet appEngineFacet, AppEngineSdk sdk, AppEngineAuthData authData,
                            ServerRuntimeInstance.DeploymentOperationCallback callback, @NotNull LoggingHandler loggingHandler) {
    myProject = project;
    myArtifact = artifact;
    myAppEngineFacet = appEngineFacet;
    mySdk = sdk;
    myAuthData = authData;
    myCallback = callback;
    myLoggingHandler = loggingHandler;
  }

  @Nullable
  public static AppEngineUploader createUploader(@NotNull Project project,
                                                 @NotNull Artifact artifact,
                                                 @NotNull AppEngineServerConfiguration configuration,
                                                 @NotNull ServerRuntimeInstance.DeploymentOperationCallback callback, @NotNull LoggingHandler loggingHandler) {
    final String explodedPath = artifact.getOutputPath();
    if (explodedPath == null) {
      callback.errorOccurred("Output path isn't specified for '" + artifact.getName() + "' artifact");
      return null;
    }

    final AppEngineFacet appEngineFacet = AppEngineUtil.findAppEngineFacet(project, artifact);
    if (appEngineFacet == null) {
      callback.errorOccurred("App Engine facet not found in '" + artifact.getName() + "' artifact");
      return null;
    }

    final AppEngineSdk sdk = appEngineFacet.getSdk();
    if (!sdk.getAppCfgFile().exists()) {
      callback.errorOccurred("Path to App Engine SDK isn't specified correctly in App Engine Facet settings");
      return null;
    }

    PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
    VirtualFile descriptorFile = ArtifactUtil.findSourceFileByOutputPath(artifact, "WEB-INF/appengine-web.xml", context);
    final AppEngineWebApp root = AppEngineFacet.getDescriptorRoot(descriptorFile, appEngineFacet.getModule().getProject());
    if (root != null) {
      final GenericDomValue<String> application = root.getApplication();
      if (StringUtil.isEmptyOrSpaces(application.getValue())) {
        final String name = Messages.showInputDialog(project, "<html>Application name is not specified in appengine-web.xml.<br>" +
              "Enter application name (see your <a href=\"http://appengine.google.com\">AppEngine account</a>):</html>", CommonBundle.getErrorTitle(), null, "", null);
        if (name == null) return null;

        final PsiFile file = application.getXmlTag().getContainingFile();
        new WriteCommandAction(project, file) {
          protected void run(@NotNull final Result result) {
            application.setStringValue(name);
          }
        }.execute();
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
        }
      }
    }

    AppEngineAuthData authData = AppEngineAccountDialog.createAuthData(project, configuration);
    if (authData == null) return null;

    return new AppEngineUploader(project, artifact, appEngineFacet, sdk, authData, callback, loggingHandler);
  }

  public void startUploading() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Uploading application", true, null) {
      public void run(@NotNull ProgressIndicator indicator) {
        compileAndUpload();
      }
    });
  }

  private void compileAndUpload() {
    final Runnable startUploading = () -> ApplicationManager.getApplication().invokeLater(() -> startUploadingProcess());

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final CompileScope moduleScope = compilerManager.createModuleCompileScope(myAppEngineFacet.getModule(), true);
    final CompileScope compileScope = ArtifactCompileScope.createScopeWithArtifacts(moduleScope, Collections.singletonList(myArtifact));
    ApplicationManager.getApplication().invokeLater(() -> compilerManager.make(compileScope, new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (!aborted && errors == 0) {
          startUploading.run();
        }
      }
    }));
  }

  private void startUploadingProcess() {

    final ProcessHandler processHandler;
    try {
      JavaParameters parameters = new JavaParameters();
      parameters.configureByModule(myAppEngineFacet.getModule(), JavaParameters.JDK_ONLY);
      parameters.setMainClass("com.google.appengine.tools.admin.AppCfg");
      parameters.getClassPath().add(mySdk.getToolsApiJarFile().getAbsolutePath());

      HttpConfigurable.getInstance().getJvmProperties(false, null).forEach(p -> parameters.getVMParametersList().addProperty(p.first, p.second));

      final ParametersList programParameters = parameters.getProgramParametersList();
      if (myAuthData.isOAuth2()) {
        programParameters.add("--oauth2");
      }
      else {
        programParameters.add("--email=" + myAuthData.getEmail());
        programParameters.add("--passin");
        programParameters.add("--no_cookies");
      }
      programParameters.add("update");
      programParameters.add(FileUtil.toSystemDependentName(myArtifact.getOutputPath()));

      final GeneralCommandLine commandLine = parameters.toCommandLine();
      processHandler = new OSProcessHandler(commandLine);
    }
    catch (ExecutionException e) {
      myCallback.errorOccurred("Cannot start uploading: " + e.getMessage());
      return;
    }

    processHandler.addProcessListener(new MyProcessListener(processHandler, null, myLoggingHandler));
    myLoggingHandler.attachToProcess(processHandler);
    processHandler.startNotify();
  }

  private class MyProcessListener extends ProcessAdapter {
    private boolean myPasswordEntered;
    private final ProcessHandler myProcessHandler;
    @Nullable private final ConsoleView myConsole;
    @Nullable private final LoggingHandler myLoggingHandler;

    public MyProcessListener(ProcessHandler processHandler, @Nullable ConsoleView console, @Nullable LoggingHandler loggingHandler) {
      myProcessHandler = processHandler;
      myConsole = console;
      myLoggingHandler = loggingHandler;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      if (!myAuthData.isOAuth2() && !myPasswordEntered && !outputType.equals(ProcessOutputTypes.SYSTEM) && event.getText().contains(myAuthData.getEmail())) {
        myPasswordEntered = true;
        final OutputStream processInput = myProcessHandler.getProcessInput();
        if (processInput != null) {
          //noinspection IOResourceOpenedButNotSafelyClosed
          final PrintWriter input = new PrintWriter(processInput);
          input.println(myAuthData.getPassword());
          input.flush();
          String message = StringUtil.repeatSymbol('*', myAuthData.getPassword().length()) + "\n";
          if (myConsole != null) {
            myConsole.print(message, ConsoleViewContentType.USER_INPUT);
          }
          else if (myLoggingHandler != null) {
            myLoggingHandler.print(message);
          }
        }
      }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      int exitCode = event.getExitCode();
      if (exitCode == 0) {
        myCallback.succeeded(new DeploymentRuntime() {
          @Override
          public boolean isUndeploySupported() {
            return false;
          }

          @Override
          public void undeploy(@NotNull UndeploymentTaskCallback callback) {
          }
        });
      }
      else {
        myCallback.errorOccurred("Process terminated with exit code " + exitCode);
      }
    }
  }
}

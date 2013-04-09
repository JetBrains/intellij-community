package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.descriptor.dom.AppEngineWebApp;
import com.intellij.appengine.facet.AppEngineAccountDialog;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
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
import com.intellij.openapi.util.KeyValue;
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
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineUploader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.actions.AppEngineUploader");
  private Project myProject;
  private final Artifact myArtifact;
  private final AppEngineFacet myAppEngineFacet;
  private final AppEngineSdk mySdk;
  private final String myEmail;
  private final String myPassword;

  private AppEngineUploader(Project project,
                            Artifact artifact,
                            AppEngineFacet appEngineFacet,
                            AppEngineSdk sdk,
                            String email,
                            String password) {
    myProject = project;
    myArtifact = artifact;
    myAppEngineFacet = appEngineFacet;
    mySdk = sdk;
    myEmail = email;
    myPassword = password;
  }

  @Nullable
  public static AppEngineUploader createUploader(@NotNull Project project, @NotNull Artifact artifact) {
    final String explodedPath = artifact.getOutputPath();
    if (explodedPath == null) {
      Messages.showErrorDialog(project, "Output path isn't specified for '" + artifact.getName() + "' artifact", CommonBundle.getErrorTitle());
      return null;
    }

    final AppEngineFacet appEngineFacet = AppEngineUtil.findAppEngineFacet(project, artifact);
    if (appEngineFacet == null) {
      Messages.showErrorDialog(project, "App Engine facet not found in '" + artifact.getName() + "' artifact", CommonBundle.getErrorTitle());
      return null;
    }

    final AppEngineSdk sdk = appEngineFacet.getSdk();
    if (!sdk.getAppCfgFile().exists()) {
      Messages.showErrorDialog(project, "Path to App Engine SDK isn't specified correctly in App Engine Facet settings", CommonBundle.getErrorTitle());
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
          protected void run(final Result result) {
            application.setStringValue(name);
          }
        }.execute();
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
        }
      }
    }

    String password = null;
    String email = null;
    try {
      email = AppEngineAccountDialog.getStoredEmail(project);
      password = AppEngineAccountDialog.getStoredPassword(project, email);
    }
    catch (PasswordSafeException e) {
      LOG.info("Cannot load stored password: " + e.getMessage());
      LOG.info(e);
    }
    if (StringUtil.isEmpty(email) || StringUtil.isEmpty(password)) {
      final AppEngineAccountDialog dialog = new AppEngineAccountDialog(project);
      dialog.show();
      if (!dialog.isOK()) return null;

      email = dialog.getEmail();
      password = dialog.getPassword();
    }

    return new AppEngineUploader(project, artifact, appEngineFacet, sdk, email, password);
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
    final Runnable startUploading = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            startUploadingProcess();
          }
        });
      }
    };

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final CompileScope moduleScope = compilerManager.createModuleCompileScope(myAppEngineFacet.getModule(), true);
    final CompileScope compileScope = ArtifactCompileScope.createScopeWithArtifacts(moduleScope, Collections.singletonList(myArtifact), true);
    if (!compilerManager.isUpToDate(compileScope)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final int answer = Messages.showYesNoDialog(myProject, "Output directory may be out of date. Do you want to build it?", "Upload Application", null);
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

  private void startUploadingProcess() {
    final Process process;
    final GeneralCommandLine commandLine;

    try {
      JavaParameters parameters = new JavaParameters();
      parameters.configureByModule(myAppEngineFacet.getModule(), JavaParameters.JDK_ONLY);
      parameters.setMainClass("com.google.appengine.tools.admin.AppCfg");
      parameters.getClassPath().add(mySdk.getToolsApiJarFile().getAbsolutePath());

      HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      final List<KeyValue<String,String>> list = HttpConfigurable.getJvmPropertiesList(false, null);
      if (! list.isEmpty()) {
        final ParametersList parametersList = parameters.getVMParametersList();
        for (KeyValue<String, String> value : list) {
          parametersList.defineProperty(value.getKey(), value.getValue());
        }
      }

      final ParametersList programParameters = parameters.getProgramParametersList();
      programParameters.add("--email=" + myEmail);
      programParameters.add("update");
      programParameters.add(FileUtil.toSystemDependentName(myArtifact.getOutputPath()));

      commandLine = CommandLineBuilder.createFromJavaParameters(parameters);
      process = commandLine.createProcess();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(myProject, "Cannot start uploading: " + e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
    final RunnerLayoutUi ui = RunnerLayoutUi.Factory.getInstance(myProject).create("Upload", "Upload Application", "Upload Application", myProject);
    final DefaultActionGroup group = new DefaultActionGroup();
    ui.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);
    ui.addContent(ui.createContent("upload", console.getComponent(), "Upload Application", null, console.getPreferredFocusableComponent()));

    final ProcessHandler processHandler = new OSProcessHandler(process, commandLine.getCommandLineString());
    processHandler.addProcessListener(new MyProcessListener(processHandler, console));
    console.attachToProcess(processHandler);
    final RunContentDescriptor contentDescriptor = new RunContentDescriptor(console, processHandler, ui.getComponent(), "Upload Application");
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
    group.add(new CloseAction(executor, contentDescriptor, myProject));

    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(executor, contentDescriptor);
    processHandler.startNotify();
  }

  private class MyProcessListener extends ProcessAdapter {
    private boolean myPasswordEntered;
    private final ProcessHandler myProcessHandler;
    private final ConsoleView myConsole;

    public MyProcessListener(ProcessHandler processHandler, ConsoleView console) {
      myProcessHandler = processHandler;
      myConsole = console;
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      if (!myPasswordEntered && !outputType.equals(ProcessOutputTypes.SYSTEM) && event.getText().contains(myEmail)) {
        myPasswordEntered = true;
        final OutputStream processInput = myProcessHandler.getProcessInput();
        if (processInput != null) {
          //noinspection IOResourceOpenedButNotSafelyClosed
          final PrintWriter input = new PrintWriter(processInput);
          input.println(myPassword);
          input.flush();
          myConsole.print(StringUtil.repeatSymbol('*', myPassword.length()) + "\n", ConsoleViewContentType.USER_INPUT);
        }
      }
    }
  }
}

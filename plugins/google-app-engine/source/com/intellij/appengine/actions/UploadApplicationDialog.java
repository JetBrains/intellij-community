package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.appengine.util.AppEngineUtil;
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
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class UploadApplicationDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JComboBox myWebFacetComboBox;
  private JComboBox mySdkComboBox;
  private final Project myProject;

  public UploadApplicationDialog(Project project) {
    super(project, true);
    setTitle("Upload Application");
    setModal(true);
    myProject = project;
    AppEngineUtil.setupWebFacetCombobox(project, myWebFacetComboBox);
    mySdkComboBox.removeAllItems();
    final List<ApplicationServer> servers = ApplicationServersManager.getInstance().getApplicationServers(AppEngineServerIntegration.getInstance());
    for (ApplicationServer server : servers) {
      mySdkComboBox.addItem(server);
    }
    mySdkComboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof ApplicationServer) {
          setText(((ApplicationServer)value).getName());
          setIcon(AppEngineServerIntegration.getInstance().getIcon());
        }
        return renderer;
      }
    });
    setOKButtonText("Upload");
    init();
    updateOKButton();
  }

  private void updateOKButton() {
    setOKActionEnabled(mySdkComboBox.getSelectedItem() != null && myWebFacetComboBox.getSelectedItem() != null);
  }

  @Override
  protected void doOKAction() {
    ApplicationServer server = (ApplicationServer)mySdkComboBox.getSelectedItem();
    final WebFacet webFacet = (WebFacet)myWebFacetComboBox.getSelectedItem();
    if (server == null || webFacet == null) return;

    final BuildConfiguration buildProperties = webFacet.getBuildConfiguration().getBuildProperties();
    final String explodedPath = buildProperties.getExplodedPath();
    if (!buildProperties.isExplodedEnabled() || explodedPath == null) {
      Messages.showErrorDialog(myMainPanel, "Exploded directory isn't specified for '" + webFacet.getName() + "' facet", CommonBundle.getErrorTitle());
      return;
    }

    final String sdkPath = ((AppEngineServerData)server.getPersistentData()).getSdkPath();
    if (sdkPath == null) {
      Messages.showErrorDialog(myMainPanel, "Path to App Engine SDK isn't specified");
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Uploading application", true, null) {
      public void run(@NotNull ProgressIndicator indicator) {
        upload(webFacet, sdkPath, explodedPath);
      }
    });
    super.doOKAction();
  }

  private void upload(WebFacet webFacet, final String sdkPath, final String explodedPath) {
    final Runnable uploadAction = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doUpload(sdkPath, explodedPath);
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
                  uploadAction.run();
                }
              }
            });
          }
          else {
            uploadAction.run();
          }
        }
      });
    }
    else {
      uploadAction.run();
    }
  }

  private void doUpload(String sdkPath, String explodedPath) {
    final String extension = SystemInfo.isWindows ? "cmd" : "sh";
    final String path = sdkPath + "/bin/appcfg." + extension;
    final ProcessBuilder processBuilder = new ProcessBuilder().command(FileUtil.toSystemDependentName(path), "update", FileUtil.toSystemDependentName(explodedPath));
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

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}

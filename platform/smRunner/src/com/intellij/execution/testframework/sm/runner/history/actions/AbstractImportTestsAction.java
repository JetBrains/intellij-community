// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.history.ImportedTestRunnableState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.Arrays;

/**
 * 1. chooses file where test results were saved
 * 2. finds the configuration element saved during export
 * 3. creates corresponding configuration with {@link SMTRunnerConsoleProperties} if configuration implements {@link SMRunnerConsolePropertiesProvider}
 *
 * Without console properties no navigation, no rerun failed is possible.
 */
public abstract class AbstractImportTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AbstractImportTestsAction.class);
  public static final String TEST_HISTORY_SIZE = "test_history_size";
  private final Executor myExecutor;

  public AbstractImportTestsAction(@Nullable @NlsActions.ActionText String text,
                                 @Nullable @NlsActions.ActionDescription String description,
                                 @Nullable Icon icon) {
    this(text, description, icon, null);
  }

  public AbstractImportTestsAction(@Nullable @NlsActions.ActionText String text,
                                   @Nullable @NlsActions.ActionDescription String description,
                                   @Nullable Icon icon,
                                   @Nullable Executor executor) {
    super(text, description, icon);
    myExecutor = executor;
  }


  public static int getHistorySize() {
    int historySize;
    try {
      historySize = Math.max(0, Integer.parseInt(PropertiesComponent.getInstance().getValue(TEST_HISTORY_SIZE, "10")));
    }
    catch (NumberFormatException e) {
      historySize = 10;
    }
    return historySize;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Nullable
  protected abstract VirtualFile getFile(@NotNull Project project);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final VirtualFile file = getFile(project);
    if (file == null) {
      return;
    }

    doImport(project, file, null, myExecutor != null ? myExecutor : DefaultRunExecutor.getRunExecutorInstance());
  }

  public static void doImport(Project project,
                              VirtualFile file,
                              Long executionId) {
    doImport(project, file, executionId, DefaultRunExecutor.getRunExecutorInstance());
  }

  private static void doImport(Project project,
                               VirtualFile file,
                               Long executionId,
                               Executor executor) {
    try {
      final ImportRunProfile profile = new ImportRunProfile(file, project, executor);
      Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
      //runner should be default to be able to execute ImportProfile, thus it's required to pass defaultExecutor
      ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(project, defaultExecutor, profile);
      //to correct icon in com.intellij.execution.runners.FakeRerunAction (appended in RunTab), let's set executor here
      builder.executor(executor);

      builder.target(profile.getTarget());
      if (executionId != null) {
        builder.executionId(executionId);
      }
      builder.buildAndExecute();
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), SmRunnerBundle.message("sm.test.runner.abstract.import.test.error.title"));
    }
  }

  public static void adjustHistory(Project project) {
    int historySize = getHistorySize();

    final File[] files = TestStateStorage.getTestHistoryRoot(project).listFiles((dir, name) -> name.endsWith(".xml"));
    if (files != null && files.length >= historySize + 1) {
      Arrays.sort(files, (o1, o2) -> {
        final long l1 = o1.lastModified();
        final long l2 = o2.lastModified();
        if (l1 == l2) return FileUtil.compareFiles(o1, o2);
        return l1 < l2 ? -1 : 1;
      });
      FileUtil.delete(files[0]);
    }
  }

  public static class ImportRunProfile implements RunProfile {
    private final VirtualFile myFile;
    private final Project myProject;
    private RunConfiguration myConfiguration;
    private boolean myImported;
    private String myTargetId;
    private final Executor myExecutor;

    public ImportRunProfile(VirtualFile file, Project project) {
      this(file, project, DefaultRunExecutor.getRunExecutorInstance());
    }

    public ImportRunProfile(VirtualFile file, Project project, Executor executor) {
      myFile = file;
      myProject = project;
      myExecutor = executor;
      class TerminateParsingException extends SAXException { }
      try (InputStream inputStream = new BufferedInputStream(new FileInputStream(VfsUtilCore.virtualToIoFile(myFile)))) {
        SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.newSAXParser().parse(inputStream, new DefaultHandler() {
          boolean isConfigContent = false;
          final StringBuilder builder = new StringBuilder();

          @Override
          public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.equals("root")) {
              throw new TerminateParsingException();
            }
            if (qName.equals("config")) {
              isConfigContent = true;
            }
            if (isConfigContent) {
              builder.append("<").append(qName);
              for (int i = 0; i < attributes.getLength(); i++) {
                builder.append(" ")
                  .append(attributes.getQName(i))
                  .append("=\"")
                  .append(JDOMUtil.escapeText(attributes.getValue(i)))
                  .append("\"");
              }
              builder.append(">");
            }
          }

          @Override
          public void characters(char[] ch, int start, int length) {
            if (isConfigContent) {
              builder.append(ch, start, length);
            }
          }

          @Override
          public void endElement(String uri, String localName, String qName) throws SAXException {
            if (isConfigContent) {
              builder.append("</").append(qName).append(">");
            }
            if (qName.equals("config")) {
              isConfigContent = false;
              try {
                Element config = JDOMUtil.load(new StringReader(builder.toString()));
                String configTypeId = config.getAttributeValue("configId");
                if (configTypeId != null) {
                  final ConfigurationType configurationType = ConfigurationTypeUtil.findConfigurationType(configTypeId);
                  if (configurationType != null) {
                    myConfiguration = configurationType.getConfigurationFactories()[0].createTemplateConfiguration(project);
                    myConfiguration.setName(config.getAttributeValue("name"));
                    myConfiguration.readExternal(config);
                    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
                    runManager.readBeforeRunTasks(config.getChild("method"),
                                                  new RunnerAndConfigurationSettingsImpl(runManager), myConfiguration);
                  }
                }
                myTargetId = config.getAttributeValue("target");
              }
              catch (Exception e) {
                LOG.debug(e);
              }
              throw new TerminateParsingException();
            }
          }
        });
      }
      catch (TerminateParsingException ignored) {
        //expected termination
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    public ExecutionTarget getTarget() {
      if (myTargetId != null) {
        if (DefaultExecutionTarget.INSTANCE.getId().equals(myTargetId)) {
          return DefaultExecutionTarget.INSTANCE;
        }
        for (ExecutionTargetProvider provider : ExecutionTargetProvider.EXTENSION_NAME.getExtensionList()) {
          for (ExecutionTarget target : provider.getTargets(myProject, myConfiguration)) {
            if (myTargetId.equals(target.getId())) {
              return target;
            }
          }
        }
        return null;
      }
      return DefaultExecutionTarget.INSTANCE;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws
                                                                                                           ExecutionException {
      if (!myImported) {
        myImported = true;
        return new ImportedTestRunnableState(this, VfsUtilCore.virtualToIoFile(myFile));
      }
      if (myConfiguration != null) {
        try {
          if (!executor.equals(myExecutor)) { //restart initial configuration with predefined executor
            ExecutionEnvironmentBuilder.create(myExecutor, myConfiguration).target(getTarget()).buildAndExecute();
            return null;
          }
          return myConfiguration.getState(executor, environment);
        }
        catch (Throwable e) {
          if (myTargetId != null && getTarget() == null) {
            throw new ExecutionException(SmRunnerBundle.message("dialog.message.target.does.not.exist", myTargetId));
          }

          LOG.info(e);
          throw new ExecutionException(SmRunnerBundle.message("dialog.message.unable.to.run.configuration.settings.are.corrupted"));
        }
      }
      throw new ExecutionException(SmRunnerBundle.message("dialog.message.unable.to.run.configuration.failed.to.detect.test.framework"));
    }

    @NotNull
    @Override
    public String getName() {
      return myImported && myConfiguration != null ? myConfiguration.getName() : myFile.getNameWithoutExtension();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myConfiguration != null ? myConfiguration.getIcon() : null;
    }

    public RunConfiguration getInitialConfiguration() {
      return myConfiguration;
    }

    public Project getProject() {
      return myProject;
    }
  }
}

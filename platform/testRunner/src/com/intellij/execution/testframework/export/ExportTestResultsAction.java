/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.testframework.export;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;

public class ExportTestResultsAction extends DumbAwareAction {
  private static final String ID = "ExportTestResults";

  private static final Logger LOG = Logger.getInstance(ExportTestResultsAction.class.getName());

  private TestFrameworkRunningModel myModel;
  private String myToolWindowId;
  private RuntimeConfiguration myRunConfiguration;

  public static ExportTestResultsAction create(String toolWindowId, RuntimeConfiguration runtimeConfiguration) {
    ExportTestResultsAction action = new ExportTestResultsAction();
    action.copyFrom(ActionManager.getInstance().getAction(ID));
    action.myToolWindowId = toolWindowId;
    action.myRunConfiguration = runtimeConfiguration;
    return action;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e.getDataContext()));
  }

  private boolean isEnabled(DataContext dataContext) {
    if (!ApplicationManagerEx.getApplicationEx().isInternal()) {
      return false;
    }

    if (myModel == null) {
      return false;
    }

    if (PlatformDataKeys.PROJECT.getData(dataContext) == null) {
      return false;
    }

    AbstractTestProxy root = myModel.getRoot();
    return !root.isInProgress() && !root.getChildren().isEmpty();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final ExportTestResultsConfiguration config = ExportTestResultsConfiguration.getInstance(project);
    LOG.assertTrue(project != null);

    String name = ExecutionBundle.message("export.test.results.filename", myRunConfiguration.getName());
    String filename = name + "." + config.getExportFormat().getDefaultExtension();
    ExportTestResultsDialog d = new ExportTestResultsDialog(project, config, filename);
    d.show();
    if (!d.isOK()) {
      return;
    }
    filename = d.getFileName();

    final String filename_ = filename;
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, ExecutionBundle.message("export.test.results.task.name"), false, new PerformInBackgroundOption() {
        @Override
        public boolean shouldStartInBackground() {
          return true;
        }

        @Override
        public void processSentToBackground() {
        }
      }) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);

          File outputFolder;
          if (StringUtil.isNotEmpty(config.getOutputFolder())) {
            if (FileUtil.isAbsolute(config.getOutputFolder())) {
              outputFolder = new File(config.getOutputFolder());
            }
            else {
              outputFolder = new File(new File(project.getLocation()), config.getOutputFolder());
            }
          }
          else {
            outputFolder = new File(project.getLocation());

          }
          final File outputFile = new File(outputFolder, filename_);
          final String outputText;
          try {
            outputText = getOutputText(project);
            if (outputText == null) {
              return;
            }
          }
          catch (IOException ex) {
            LOG.warn(ex);
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", ex.getMessage()), null);
            return;
          }
          catch (TransformerException ex) {
            LOG.warn(ex);
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", ex.getMessage()), null);
            return;
          }
          catch (SAXException ex) {
            LOG.warn(ex);
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", ex.getMessage()), null);
            return;
          }

          final Ref<VirtualFile> result = new Ref<VirtualFile>();
          final Ref<String> error = new Ref<String>();
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
              result.set(ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                @Override
                public VirtualFile compute() {
                  outputFile.getParentFile().mkdirs();
                  final VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile.getParentFile());
                  if (parent == null || !parent.isValid()) {
                    error.set(ExecutionBundle.message("failed.to.create.output.file: ''" + outputFile.getPath() + "''"));
                    return null;
                  }

                  try {
                    VirtualFile result = parent.createChildData(this, outputFile.getName());
                    VfsUtil.saveText(result, outputText);
                    return result;
                  }
                  catch (IOException e) {
                    LOG.warn(e);
                    error.set(e.getMessage());
                    return null;
                  }
                }
              }));
            }
          }, ModalityState.defaultModalityState());

          if (!result.isNull()) {
            if (config.isOpenResults()) {
              openEditorOrBrowser(result.get(), project, config.getExportFormat() == ExportTestResultsConfiguration.ExportFormat.Xml);
            }
            else {
              HyperlinkListener listener = new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                  if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    openEditorOrBrowser(result.get(), project, config.getExportFormat() == ExportTestResultsConfiguration.ExportFormat.Xml);
                  }
                }
              };
              showBalloon(project, MessageType.INFO, ExecutionBundle.message("export.test.results.succeeded", outputFile.getName()),
                          listener);
            }
          }
          else {
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", error.get()), null);
          }
        }
      });
  }

  private static void openEditorOrBrowser(final VirtualFile result, final Project project, final boolean editor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (editor) {
          FileEditorManager.getInstance(project).openFile(result, true);
        }
        else {
          BrowserUtil.launchBrowser(result.getUrl());
        }
      }
    });
  }

  @Nullable
  private String getOutputText(Project project) throws IOException, TransformerException, SAXException {
    ExportTestResultsConfiguration config = ExportTestResultsConfiguration.getInstance(project);
    ExportTestResultsConfiguration.ExportFormat exportFormat = config.getExportFormat();

    SAXTransformerFactory transformerFactory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
    TransformerHandler handler;
    if (exportFormat == ExportTestResultsConfiguration.ExportFormat.Xml) {
      handler = transformerFactory.newTransformerHandler();
      handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
      handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    }
    else {
      Source xslSource;
      if (config.getExportFormat() == ExportTestResultsConfiguration.ExportFormat.BundledTemplate) {
        URL bundledXsltUrl = getClass().getResource("junit-noframes.xsl");
        xslSource = new StreamSource(URLUtil.openStream(bundledXsltUrl));
      }
      else {
        File xslFile = new File(config.getUserTemplatePath());
        if (!xslFile.isFile()) {
          showBalloon(myRunConfiguration.getProject(), MessageType.ERROR,
                      ExecutionBundle.message("export.test.results.custom.template.not.found", xslFile.getPath()), null);
          return null;
        }
        xslSource = new StreamSource(xslFile);
      }
      handler = transformerFactory.newTransformerHandler(xslSource);
      handler.getTransformer().setParameter("TITLE", ExecutionBundle.message("export.test.results.filename", myRunConfiguration.getName(),
                                                                             myRunConfiguration.getType().getDisplayName()));
    }

    StringWriter w = new StringWriter();
    handler.setResult(new StreamResult(w));
    TestResultsXmlFormatter.execute(myModel.getRoot(), myRunConfiguration, handler);
    return w.toString();
  }

  private void showBalloon(final Project project, final MessageType type, final String text, @Nullable final HyperlinkListener listener) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        if (ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId) != null) {
          ToolWindowManager.getInstance(project).notifyByBalloon(myToolWindowId, type, text, null, listener);
        }
      }
    });
  }

}

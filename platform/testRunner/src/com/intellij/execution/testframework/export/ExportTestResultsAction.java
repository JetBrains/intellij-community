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
package com.intellij.execution.testframework.export;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
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
  private RunConfiguration myRunConfiguration;

  public static ExportTestResultsAction create(String toolWindowId, RunConfiguration runtimeConfiguration) {
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
    if (myModel == null) {
      return false;
    }

    if (CommonDataKeys.PROJECT.getData(dataContext) == null) {
      return false;
    }

    return !myModel.getRoot().isInProgress();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    LOG.assertTrue(project != null);
    final ExportTestResultsConfiguration config = ExportTestResultsConfiguration.getInstance(project);

    final String name = ExecutionBundle.message("export.test.results.filename", PathUtil.suggestFileName(myRunConfiguration.getName()));
    String filename = name + "." + config.getExportFormat().getDefaultExtension();
    boolean showDialog = true;
    while (showDialog) {
      final ExportTestResultsDialog d = new ExportTestResultsDialog(project, config, filename);
      if (!d.showAndGet()) {
        return;
      }
      filename = d.getFileName();
      showDialog = getOutputFile(config, project, filename).exists()
                   && Messages.showOkCancelDialog(
        project,
        ExecutionBundle.message("export.test.results.file.exists.message", filename),
        ExecutionBundle.message("export.test.results.file.exists.title"),
        Messages.getQuestionIcon()
      ) != Messages.OK;
    }

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

          final File outputFile = getOutputFile(config, project, filename_);
          final String outputText;
          try {
            outputText = getOutputText(config);
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
          catch (RuntimeException ex) {
            ExportTestResultsConfiguration c = new ExportTestResultsConfiguration();
            c.setExportFormat(ExportTestResultsConfiguration.ExportFormat.Xml);
            c.setOpenResults(false);
            try {
              String xml = getOutputText(c);
              LOG.error(LogMessageEx.createEvent("Failed to export test results", ExceptionUtil.getThrowableText(ex), null, null,
                                                 new Attachment("dump.xml", xml)));
            }
            catch (Throwable ignored) {
              LOG.error("Failed to export test results", ex);
            }
            return;
          }

          final Ref<VirtualFile> result = new Ref<>();
          final Ref<String> error = new Ref<>();
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
              result.set(ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                @Override
                public VirtualFile compute() {
                  outputFile.getParentFile().mkdirs();
                  final VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile.getParentFile());
                  if (parent == null || !parent.isValid()) {
                    error.set(ExecutionBundle.message("failed.to.create.output.file", outputFile.getPath()));
                    return null;
                  }

                  try {
                    VirtualFile result = parent.findChild(outputFile.getName());
                    if (result == null) {
                      result = parent.createChildData(this, outputFile.getName());
                    }
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

  @NotNull
  private static File getOutputFile(
    final @NotNull ExportTestResultsConfiguration config,
    final @NotNull Project project,
    final @NotNull String filename)
  {
    final File outputFolder;
    final String outputFolderPath = config.getOutputFolder();
    if (!StringUtil.isEmptyOrSpaces(outputFolderPath)) {
      if (FileUtil.isAbsolute(outputFolderPath)) {
        outputFolder = new File(outputFolderPath);
      }
      else {
        outputFolder = new File(new File(project.getBasePath()), config.getOutputFolder());
      }
    }
    else {
      outputFolder = new File(project.getBasePath());
    }

    return new File(outputFolder, filename);
  }

  private static void openEditorOrBrowser(final VirtualFile result, final Project project, final boolean editor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (editor) {
        FileEditorManager.getInstance(project).openFile(result, true);
      }
      else {
        BrowserUtil.browse(result);
      }
    });
  }

  @Nullable
  private String getOutputText(ExportTestResultsConfiguration config) throws IOException, TransformerException, SAXException {
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
        URL bundledXsltUrl = getClass().getResource("intellij-export.xsl");
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
    try {
      TestResultsXmlFormatter.execute(myModel.getRoot(), myRunConfiguration, myModel.getProperties(), handler);
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    return w.toString();
  }

  private void showBalloon(final Project project, final MessageType type, final String text, @Nullable final HyperlinkListener listener) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) return;
      if (ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId) != null) {
        ToolWindowManager.getInstance(project).notifyByBalloon(myToolWindowId, type, text, null, listener);
      }
    });
  }

}

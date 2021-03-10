// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.export;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ExportTestResultsAction extends DumbAwareAction {
  private static final String ID = "ExportTestResults";

  private static final Logger LOG = Logger.getInstance(ExportTestResultsAction.class.getName());

  private TestFrameworkRunningModel myModel;
  private String myToolWindowId;
  private RunConfiguration myRunConfiguration;

  public static ExportTestResultsAction create(String toolWindowId, RunConfiguration runtimeConfiguration, JComponent component) {
    ExportTestResultsAction action = new ExportTestResultsAction();
    ActionUtil.copyFrom(action, ID);
    action.myToolWindowId = toolWindowId;
    action.myRunConfiguration = runtimeConfiguration;
    action.registerCustomShortcutSet(action.getShortcutSet(), component);
    return action;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
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
  public void actionPerformed(@NotNull AnActionEvent e) {
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
                   && Messages.showOkCancelDialog(project,
                                                  ExecutionBundle.message("export.test.results.file.exists.message", filename),
                                                  ExecutionBundle.message("export.test.results.file.exists.title"),
                                                  TestRunnerBundle.message("export.test.results.overwrite.button.text"),
                                                  CommonBundle.getCancelButtonText(),
                                                  Messages.getQuestionIcon()
      ) != Messages.OK;
    }

    final File outputFile = getOutputFile(config, project, filename);
    final VirtualFile parent = outputFile.getParentFile().mkdirs() ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile.getParentFile()) 
                                                                   : null;
    if (parent == null || !parent.isValid()) {
      showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", 
                                                                      ExecutionBundle.message("failed.to.create.output.file", outputFile.getPath())), null);
      return;
    }
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, ExecutionBundle.message("export.test.results.task.name"), false, new PerformInBackgroundOption() {
        @Override
        public boolean shouldStartInBackground() {
          return true;
        }
      }) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          try {
            if (!writeOutputFile(config, outputFile)) return;
          }
          catch (IOException | SAXException | TransformerException ex) {
            LOG.warn(ex);
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", ex.getMessage()), null);
            return;
          }
          catch (RuntimeException ex) {
            
            File tempFile;
            try {
              tempFile = FileUtil.createTempFile("", "_xml");
            }
            catch (IOException exception) {
              LOG.error("Failed to create temp file", exception);
              LOG.error("Failed to export test results", ex);
              return;
            }

            try {
              ExportTestResultsConfiguration c = new ExportTestResultsConfiguration();
              c.setExportFormat(ExportTestResultsConfiguration.ExportFormat.Xml);
              c.setOpenResults(false);
              writeOutputFile(c, tempFile);
            }
            catch (Throwable ignored) { }

            LOG.error("Failed to export test results", ex, AttachmentFactory.createAttachment(tempFile, false));
            FileUtil.delete(tempFile);
            return;
          }

          final Ref<VirtualFile> result = new Ref<>();
          final Ref<String> error = new Ref<>();
          ApplicationManager.getApplication().invokeAndWait(() -> {
            result.set(WriteAction.compute(() -> {
              try {
                VirtualFile child = parent.findChild(outputFile.getName());
                return child == null ? parent.createChildData(this, outputFile.getName()) : child;
              }
              catch (IOException e) {
                LOG.warn(e);
                error.set(e.getMessage());
                return null;
              }
            }));
          });
            

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

  private boolean writeOutputFile(ExportTestResultsConfiguration config, File outputFile) throws IOException, TransformerException, SAXException {
    switch (config.getExportFormat()) {
      case Xml:
        TransformerHandler handler = ((SAXTransformerFactory)TransformerFactory.newInstance()).newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");  // NON-NLS
        return transform(outputFile, handler);
      case BundledTemplate:
        try (InputStream bundledXsltUrl = getClass().getResourceAsStream("intellij-export.xsl")) {
          return transformWithXslt(outputFile, new StreamSource(bundledXsltUrl));
        }
      case UserTemplate:
        File xslFile = new File(config.getUserTemplatePath());
        if (!xslFile.isFile()) {
          showBalloon(myRunConfiguration.getProject(), MessageType.ERROR,
                      ExecutionBundle.message("export.test.results.custom.template.not.found", xslFile.getPath()), null);
          return false;
        }
        return transformWithXslt(outputFile, new StreamSource(xslFile));
      default:
        throw new IllegalArgumentException();
    }
  }

  private boolean transformWithXslt(File outputFile, Source xslSource)
    throws TransformerConfigurationException, IOException, SAXException {
    TransformerHandler handler = ((SAXTransformerFactory)TransformerFactory.newInstance()).newTransformerHandler(xslSource);
    handler.getTransformer().setParameter("TITLE", ExecutionBundle.message("export.test.results.filename", myRunConfiguration.getName(),
                                                                           myRunConfiguration.getType().getDisplayName()));

    return transform(outputFile, handler);
  }

  private boolean transform(File outputFile, TransformerHandler handler) throws IOException, SAXException {
    try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))) {
      handler.setResult(new StreamResult(w));
      TestResultsXmlFormatter.execute(myModel.getRoot(), myRunConfiguration, myModel.getProperties(), handler);
      return true;
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  private void showBalloon(final Project project,
                           final MessageType type,
                           final @NlsContexts.PopupContent String text,
                           final @Nullable HyperlinkListener listener) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) return;
      if (ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId) != null) {
        ToolWindowManager.getInstance(project).notifyByBalloon(myToolWindowId, type, text, null, listener);
      }
    });
  }

}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.export;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestRunnerBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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
      Path outputFile = getOutputFile(config, project, filename);
      showDialog = outputFile != null && Files.exists(outputFile)
                   && Messages.showOkCancelDialog(project,
                                                  ExecutionBundle.message("export.test.results.file.exists.message", filename),
                                                  ExecutionBundle.message("export.test.results.file.exists.title"),
                                                  TestRunnerBundle.message("export.test.results.overwrite.button.text"),
                                                  CommonBundle.getCancelButtonText(),
                                                  Messages.getQuestionIcon()
      ) != Messages.OK;
    }

    final Path outputFile = getOutputFile(config, project, filename);
    Path parentFile = createDirectories(outputFile != null ? outputFile.getParent() : null);
    final VirtualFile parent = parentFile != null 
                               ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentFile)
                               : null;
    if (parent == null || !parent.isValid()) {
      String filePath = outputFile != null ? outputFile.toString() : filename;
      showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", 
                                                                      ExecutionBundle.message("failed.to.create.output.file", filePath)), null);
      return;
    }
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, ExecutionBundle.message("export.test.results.task.name"), false,
                              PerformInBackgroundOption.ALWAYS_BACKGROUND) {
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
          catch (ProcessCanceledException pce) {
            throw pce;
          }
          catch (RuntimeException ex) {
            
            Path tempFile;
            try {
              tempFile = Files.createTempFile("", "_xml");
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
            try {
              NioFiles.deleteRecursively(tempFile);
            }
            catch (IOException e) {
              LOG.warn("Failed to delete temp file", e);
            }
            return;
          }

          final Ref<VirtualFile> result = new Ref<>();
          final Ref<String> error = new Ref<>();
          ApplicationManager.getApplication().invokeAndWait(() -> {
            result.set(WriteAction.compute(() -> {
              try {
                String fileName = outputFile.getFileName().toString();
                VirtualFile child = parent.findChild(fileName);
                return child == null ? parent.createChildData(this, fileName) : child;
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
              showBalloon(project, MessageType.INFO, ExecutionBundle.message("export.test.results.succeeded", outputFile.getFileName().toString()),
                          listener);
            }
          }
          else {
            showBalloon(project, MessageType.ERROR, ExecutionBundle.message("export.test.results.failed", error.get()), null);
          }
        }
      });
  }

  private static @Nullable Path createDirectories(@Nullable Path path) {
    if (path == null) return path;
    try {
      return Files.createDirectories(path);
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable Path getOutputFile(
    final @NotNull ExportTestResultsConfiguration config,
    final @NotNull Project project,
    final @NotNull String filename)
  {
    try {
      Path outputFolder = getOutputFolder(config, project);
      return outputFolder != null ? outputFolder.resolve(filename) : null;
    }
    catch (InvalidPathException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable Path getOutputFolder(@NotNull ExportTestResultsConfiguration config, @NotNull Project project) {
    String outputFolderStr = config.getOutputFolder();
    if (!StringUtil.isEmptyOrSpaces(outputFolderStr)) {
      Path outputFolder = Path.of(outputFolderStr);
      if (outputFolder.isAbsolute()) {
        return outputFolder;
      }
    }
    String basePathStr = project.getBasePath();
    if (basePathStr == null) return null;
    Path basePath = Path.of(basePathStr);
    return StringUtil.isEmptyOrSpaces(outputFolderStr) ? basePath : basePath.resolve(outputFolderStr);
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

  private boolean writeOutputFile(ExportTestResultsConfiguration config, @NotNull Path outputFile) throws IOException, TransformerException, SAXException {
    try {
      writeOutputFile(new ExportContext(
        config.getExportFormat(),
        config.getUserTemplatePath(),
        myModel.getRoot(),
        myRunConfiguration,
        myModel.getProperties(),
        outputFile
      ));
      return true;
    }
    catch (NonExistentUserTemplatePathException e) {
      showBalloon(myRunConfiguration.getProject(), MessageType.ERROR, e.getMessage(), null);
      return false;
    }
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public static void writeOutputFile(@NotNull ExportContext context) throws IOException, TransformerException, SAXException {
    switch (context.exportFormat) {
      case Xml -> {
        TransformerHandler handler = ((SAXTransformerFactory)TransformerFactory.newDefaultInstance()).newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");  // NON-NLS
        transform(context, handler);
      }
      case BundledTemplate -> {
        try (InputStream bundledXsltUrl = ExportTestResultsAction.class.getResourceAsStream("intellij-export.xsl")) {
          transformWithXslt(context, new StreamSource(bundledXsltUrl));
        }
      }
      case UserTemplate -> {
        String userTemplatePath = context.userTemplatePath;
        Path xslFile = StringUtil.isEmptyOrSpaces(userTemplatePath) ? null : NioFiles.toPath(userTemplatePath);
        if (xslFile == null || !Files.isRegularFile(xslFile)) {
          throw new NonExistentUserTemplatePathException(userTemplatePath);
        }
        transformWithXslt(context, new StreamSource(xslFile.toUri().toASCIIString()));
      }
      default -> throw new IllegalArgumentException();
    }
  }

  private static void transformWithXslt(@NotNull ExportContext context, @NotNull Source xslSource)
    throws TransformerConfigurationException, IOException, SAXException {
    SAXTransformerFactory transformerFactory = (SAXTransformerFactory)TransformerFactory.newDefaultInstance();
    // Enable extension functions to use `str:tokenize` in `intellij-export.xsl`
    // https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/module-summary.html#jdk.xml.enableExtensionFunctions
    transformerFactory.setFeature("jdk.xml.enableExtensionFunctions", true);
    TransformerHandler handler = transformerFactory.newTransformerHandler(xslSource);
    handler.getTransformer().setParameter("TITLE", ExecutionBundle.message("export.test.results.filename", context.runConfiguration.getName(),
                                                                           context.runConfiguration.getType().getDisplayName()));

    transform(context, handler);
  }

  private static void transform(@NotNull ExportContext context, TransformerHandler handler) throws IOException, SAXException {
    try (BufferedWriter w = Files.newBufferedWriter(context.outputFile, StandardCharsets.UTF_8)) {
      handler.setResult(new StreamResult(w));
      TestResultsXmlFormatter.execute(context.root, context.runConfiguration, context.properties, handler);
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

  private static class NonExistentUserTemplatePathException extends RuntimeException {
    NonExistentUserTemplatePathException(@Nullable String userTemplatePath) {
      super(ExecutionBundle.message("export.test.results.custom.template.not.found", Objects.requireNonNullElse(userTemplatePath, "N/A")));
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public record ExportContext(
    @NotNull ExportTestResultsConfiguration.ExportFormat exportFormat,
    @Nullable String userTemplatePath,
    @NotNull AbstractTestProxy root,
    @NotNull RunConfiguration runConfiguration,
    @NotNull TestConsoleProperties properties,
    @NotNull Path outputFile
  ) {}
}

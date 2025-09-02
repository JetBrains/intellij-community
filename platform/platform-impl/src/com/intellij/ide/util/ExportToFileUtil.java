// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TooManyListenersException;

public final class ExportToFileUtil {
  private static final Logger LOG = Logger.getInstance(ExportToFileUtil.class);

  @RequiresEdt
  public static void chooseFileAndExport(@NotNull Project project, @NotNull ExporterToTextFile exporter) {
    final ExportDialogBase dlg = new ExportDialogBase(project, exporter);

    if (!dlg.showAndGet()) {
      return;
    }

    exportTextToFile(project, dlg.getFileName(), dlg.getText());
    exporter.exportedTo(dlg.getFileName());
  }

  private static void exportTextToFile(Project project, String fileName, String textToExport) {
    boolean append = false;
    File file = new File(fileName);
    if (file.exists()) {
      int result = Messages.showYesNoCancelDialog(
        project,
        IdeBundle.message("error.text.file.already.exists", fileName),
        IdeBundle.message("dialog.title.export.to.file"),
        IdeBundle.message("action.overwrite"),
        IdeBundle.message("action.append"),
        CommonBundle.getCancelButtonText(),
        Messages.getWarningIcon()
      );

      if (result != Messages.NO && result != Messages.YES) {
        return;
      }
      if (result == Messages.NO) {
        append = true;
      }
    }

    try (FileWriter writer = new FileWriter(fileName, StandardCharsets.UTF_8, append)) {
      writer.write(textToExport);
    }
    catch (IOException e) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("error.writing.to.file", fileName),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  private static final class ExportDialogBase extends DialogWrapper {
    private final Project myProject;
    private final ExporterToTextFile myExporter;
    private Editor myTextArea;
    private TextFieldWithBrowseButton myTfFile;
    private ChangeListener myListener;

    ExportDialogBase(Project project, ExporterToTextFile exporter) {
      super(project, true);
      myProject = project;
      myExporter = exporter;

      myTfFile = new TextFieldWithBrowseButton();
      myTfFile.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(), myProject) {
        @Override
        protected @NotNull String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
          String res = super.chosenFileToResultingText(chosenFile);
          if (chosenFile.isDirectory()) {
            res += File.separator + PathUtil.getFileName(myExporter.getDefaultFilePath());
          }
          return res;
        }
      });

      setTitle(IdeBundle.message("title.export.preview"));
      setOKButtonText(IdeBundle.message("button.save"));
      init();
      try {
        myListener = new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            initText();
          }
        };
        myExporter.addSettingsChangedListener(myListener);
      }
      catch (TooManyListenersException e) {
        LOG.error(e);
      }
      initText();
    }

    @Override
    public void dispose() {
      myExporter.removeSettingsChangedListener(myListener);
      EditorFactory.getInstance().releaseEditor(myTextArea);
      super.dispose();
    }

    private void initText() {
      myTextArea.getDocument().setText(myExporter.getReportText());
    }

    @Override
    protected JComponent createCenterPanel() {
      final Document document = ((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(true);
      ((DocumentImpl)document).setAcceptSlashR(true);

      myTextArea = EditorFactory.getInstance().createEditor(document, myProject, FileTypes.PLAIN_TEXT, true);
      final EditorSettings settings = myTextArea.getSettings();
      settings.setLineNumbersShown(false);
      settings.setLineMarkerAreaShown(false);
      settings.setFoldingOutlineShown(false);
      settings.setRightMarginShown(false);
      settings.setAdditionalLinesCount(0);
      settings.setAdditionalColumnsCount(0);
      settings.setAdditionalPageAtBottom(false);

      EditorEx editorEx = (EditorEx)myTextArea;
      editorEx.setBackgroundColor(UIUtil.getInactiveTextFieldBackgroundColor());
      editorEx.setColorsScheme(EditorColorsManager.getInstance().getSchemeForCurrentUITheme());
      editorEx.setHighlighter(new EmptyEditorHighlighter());

      myTextArea.getComponent().setPreferredSize(new Dimension(700, 400));
      return myTextArea.getComponent();
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel filePanel = createFilePanel();
      JComponent settingsPanel = myExporter.getSettingsEditor();
      if (settingsPanel == null) {
        return filePanel;
      }
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(filePanel, BorderLayout.NORTH);
      northPanel.add(settingsPanel, BorderLayout.CENTER);
      return northPanel;
    }

    private JPanel createFilePanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new GridBagLayout());
      GridBagConstraints gbConstraints = new GridBagConstraints();
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      JLabel promptLabel = new JLabel(IdeBundle.message("editbox.export.to.file"));
      gbConstraints.weightx = 0;
      panel.add(promptLabel, gbConstraints);
      gbConstraints.weightx = 1;
      panel.add(myTfFile, gbConstraints);

      String defaultFilePath = myExporter.getDefaultFilePath();
      if (!new File(defaultFilePath).isAbsolute()) {
        defaultFilePath = PathMacroManager.getInstance(myProject).collapsePath(defaultFilePath);
      }
      myTfFile.setText(FileUtil.toSystemDependentName(defaultFilePath));

      panel.setBorder(JBUI.Borders.emptyBottom(5));

      return panel;
    }

    public String getText() {
      return myTextArea.getDocument().getText();
    }

    public void setFileName(@NlsSafe String s) {
      myTfFile.setText(s);
    }

    public String getFileName() {
      return myTfFile.getText();
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction(), new CopyToClipboardAction(), getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.util.ExportDialog";
    }

    protected final class CopyToClipboardAction extends AbstractAction {
      public CopyToClipboardAction() {
        super(IdeBundle.message("button.copy"));
        putValue(Action.SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"));
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        String s = StringUtil.convertLineSeparators(getText());
        CopyPasteManager.getInstance().setContents(new StringSelection(s));
      }
    }
  }
}

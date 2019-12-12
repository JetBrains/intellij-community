// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.lang.Language;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

public class AddGroupToLocalWhitelistDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(AddGroupToLocalWhitelistDialog.class);
  private JPanel myMainPanel;
  private JTextField myGroupIdTextField;
  private JBLabel myGroupIdLabel;
  private ComboBox<String> myRecorderComboBox;
  private JBLabel myRecorderLabel;
  private JPanel myAddCustomRulePanel;
  private JCheckBox myAddCustomRuleCheckBox;

  @SuppressWarnings("unused")
  private ContextHelpLabel myContextHelpLabel;

  private final Project myProject;
  private final EditorEx myValidationRulesEditor;
  private final List<PsiFile> myTempFiles = new ArrayList<>();

  protected AddGroupToLocalWhitelistDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    myRecorderLabel.setLabelFor(myRecorderComboBox);
    myGroupIdLabel.setLabelFor(myGroupIdTextField);

    myValidationRulesEditor = initEditor(project, myAddCustomRulePanel);
    myAddCustomRuleCheckBox.setSelected(false);
    myAddCustomRuleCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
        updateRulesOption();
      }
    });
    updateRulesOption();
    setOKButtonText("&Add");
    myMainPanel.setPreferredSize(new Dimension(500, 200));
    Disposer.register(project, getDisposable());
    setTitle("Add Test Group to Local Whitelist");
    init();
  }

  private void updateRulesOption() {
    final boolean customRules = myAddCustomRuleCheckBox.isSelected();
    myAddCustomRulePanel.setVisible(customRules);
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "#com.intellij.internal.statistic.actions.AddGroupToLocalWhitelistDialog";
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGroupIdTextField;
  }

  public boolean isCustomRules() {
    return myAddCustomRuleCheckBox.isSelected();
  }

  @NotNull
  public String getCustomRules() {
    return myValidationRulesEditor.getDocument().getText();
  }

  @Nullable
  public String getGroupId() {
    return StringUtil.nullize(myGroupIdTextField.getText());
  }

  @Nullable
  public String getRecorderId() {
    final Object item = myRecorderComboBox.getSelectedItem();
    return item instanceof String ? (String)item : null;
  }

  @NotNull
  private EditorEx initEditor(@NotNull Project project, @NotNull JPanel panel) {
    final String templateText =
      "{\n" +
      "  \"event_id\": [],\n" +
      "  \"event_data\": {\n  }\n" +
      "}";
    final PsiFile file = createTempFile(project, "event-log-validation-rules", templateText);
    assert file != null;

    myTempFiles.add(file);
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) {
      document = EditorFactory.getInstance().createDocument(templateText);
    }

    final EditorEx editor = (EditorEx)EditorFactory.getInstance().createEditor(document, project, file.getVirtualFile(), false);
    editor.setFile(file.getVirtualFile());
    editor.getSettings().setLineMarkerAreaShown(false);

    panel.setLayout(new BorderLayout());
    panel.add(editor.getComponent(), BorderLayout.CENTER);

    editor.getSettings().setFoldingOutlineShown(false);
    final FileType fileType = FileTypeManager.getInstance().findFileTypeByName("JSON");
    final LightVirtualFile lightFile = new LightVirtualFile("Dummy.json", fileType, "");

    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, lightFile);
    try {
      editor.setHighlighter(highlighter);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
    return editor;
  }

  @SuppressWarnings("SameParameterValue")
  @Nullable
  private static PsiFile createTempFile(@NotNull Project project, @NotNull String filename, @NotNull String request) {
    final String fileName = PathUtil.makeFileName(filename, "json");
    try {
      final ThrowableComputable<PsiFile, Exception> computable = () -> {
        final ScratchFileService fileService = ScratchFileService.getInstance();
        final VirtualFile file =
          fileService.findFile(RootType.findById("scratches"), fileName, ScratchFileService.Option.create_if_missing);

        fileService.getScratchesMapping().setMapping(file, Language.findLanguageByID("JSON"));
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        final Document document = psiFile != null ? PsiDocumentManager.getInstance(project).getDocument(psiFile) : null;
        if (document == null) {
          return null;
        }

        document.insertString(document.getTextLength(), request);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        return psiFile;
      };

      return writeCommandAction(project)
        .withName("Creating temp JSON file for event log")
        .withGlobalUndo().shouldRecordActionForActiveDocument(false)
        .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
        .compute(computable);
    }
    catch (Exception e) {
      // ignore
    }
    return null;
  }

  private void createUIComponents() {
    myRecorderComboBox = new ComboBox<>();
    StatisticsEventLoggerKt.getEventLogProviders().stream().
      map(provider -> provider.getRecorderId()).
      forEach(id -> myRecorderComboBox.addItem(id));

    final String description = "Should be used before whitelisting to test that validation rules for the group work correctly";
    myContextHelpLabel = ContextHelpLabel.create(description);
  }

  @Override
  public void dispose() {
    writeCommandAction(myProject).run(() -> {
      for (PsiFile file : myTempFiles) {
        try {
          file.delete();
        }
        catch (IncorrectOperationException e) {
          LOG.warn(e);
        }
      }
    });

    if (!myValidationRulesEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myValidationRulesEditor);
    }
    super.dispose();
  }
}

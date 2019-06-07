// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class ReplacementPreviewDialog extends DialogWrapper {
  private final LanguageFileType myFileType;
  private Editor replacement;

  private final Project project;
  private RangeHighlighter myHighlighter;
  private Editor editor;


  public ReplacementPreviewDialog(@NotNull Project project, @NotNull UsageInfo info, String replacementString) {
    super(project, true);

    setTitle(SSRBundle.message("structural.replace.preview.dialog.title"));
    setOKButtonText(SSRBundle.message("replace.preview.oktext"));
    this.project = project;
    final PsiElement element = info.getElement();
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    myFileType = virtualFile != null ? (LanguageFileType)virtualFile.getFileType() : FileTypes.PLAIN_TEXT;
    init();

    Segment range = info.getSegment();
    highlight(virtualFile, range.getStartOffset(), range.getEndOffset());
    UIUtil.setContent(replacement, replacementString);

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    if (profile != null) {
      TemplateEditorUtil.setHighlighter(replacement, UIUtil.getTemplateContextType(profile));
    }
  }

  private void highlight(VirtualFile file, int start, int end) {
    removeHighlighter();

    editor = FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, file),
      false
    );
    myHighlighter = editor.getMarkupModel().addRangeHighlighter(
      start,
      end,
      HighlighterLayer.SELECTION - 100,
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES),
      HighlighterTargetArea.EXACT_RANGE
    );
  }

  private void removeHighlighter() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      myHighlighter.dispose();
      myHighlighter = null;
      editor = null;
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.strucuturalsearch.plugin.replace.ReplacementPreviewDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    final JComponent centerPanel = new JPanel(new BorderLayout() );

    PsiFile file = null;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      file = profile.createCodeFragment(project, "", null);
    }

    if (file != null) {
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      replacement = UIUtil.createEditor(document, project, true, null);
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
    } else {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document document = factory.createDocument("");
      replacement = factory.createEditor(document, project, myFileType, false);
    }

    centerPanel.add(BorderLayout.NORTH,new JLabel(SSRBundle.message("replacement.code")) );
    centerPanel.add(BorderLayout.CENTER,replacement.getComponent() );
    centerPanel.setMaximumSize(new Dimension(640,480));

    return centerPanel;
  }

  @Override
  public void dispose() {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(replacement.getDocument());
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, true);
    }

    EditorFactory.getInstance().releaseEditor(replacement);
    removeHighlighter();

    super.dispose();
  }
}


package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
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

import javax.swing.*;
import java.awt.*;

/**
 * Navigates through the search results
 */
public final class ReplacementPreviewDialog extends DialogWrapper {
  private final FileType myFileType;
  private Editor replacement;

  private final Project project;
  private RangeHighlighter hilighter;
  private Editor editor;


  public ReplacementPreviewDialog(final Project project, UsageInfo info, String replacementString) {
    super(project,true);

    setTitle(SSRBundle.message("structural.replace.preview.dialog.title"));
    setOKButtonText(SSRBundle.message("replace.preview.oktext"));
    this.project = project;
    final PsiElement element = info.getElement();
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    myFileType = virtualFile != null ? virtualFile.getFileType() : FileTypes.PLAIN_TEXT;
    init();

    Segment range = info.getSegment();
    hilight(virtualFile, range.getStartOffset(), range.getEndOffset());
    UIUtil.setContent(replacement, replacementString);

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    if (profile != null) {
      UIUtil.updateHighlighter(replacement, profile);
    }
  }

  private void hilight(VirtualFile file,int start, int end) {
    removeHilighter();

    editor = FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, file),
      false
    );
    hilighter = editor.getMarkupModel().addRangeHighlighter(
      start,
      end,
      HighlighterLayer.SELECTION - 100,
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES),
      HighlighterTargetArea.EXACT_RANGE
    );
  }

  private void removeHilighter() {
    if (hilighter!=null && hilighter.isValid()) {
      hilighter.dispose();
      hilighter = null;
      editor = null;
    }
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.strucuturalsearch.plugin.replace.ReplacementPreviewDialog";
  }

  protected JComponent createCenterPanel() {
    JComponent centerPanel = new JPanel( new BorderLayout() );

    PsiFile file = null;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(myFileType);
    if (profile != null) {
      file = profile.createCodeFragment(project, "", null);
    }

    if (file != null) {
      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      replacement = UIUtil.createEditor(document, project, true, null);
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file,false);
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

  public void dispose() {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(replacement.getDocument());
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, true);
    }

    EditorFactory.getInstance().releaseEditor(replacement);
    removeHilighter();

    super.dispose();
  }
}


/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class UsagePreviewPanel extends UsageContextPanelBase implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsagePreviewPanel");
  private Editor myEditor;
  private final boolean myIsEditor;
  private int myLineHeight;
  private List<UsageInfo> myCachedSelectedUsageInfos;
  private Pattern myCachedSearchPattern = null;
  private Pattern myCachedReplacePattern = null;

  public UsagePreviewPanel(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    this(project, presentation, false);
  }

  public UsagePreviewPanel(@NotNull Project project,
                           @NotNull UsageViewPresentation presentation,
                           boolean isEditor) {
    super(project, presentation);
    myIsEditor = isEditor;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.EDITOR.getName().equals(dataId) && myEditor != null) {
      return myEditor;
    }
    return null;
  }

  public static class Provider implements UsageContextPanel.Provider {
    @NotNull
    @Override
    public UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsagePreviewPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation(), true);
    }

    @Override
    public boolean isAvailableFor(@NotNull UsageView usageView) {
      return true;
    }
    @NotNull
    @Override
    public String getTabTitle() {
      return "Preview";
    }
  }

  private void resetEditor(@NotNull final List<UsageInfo> infos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiElement psiElement = infos.get(0).getElement();
    if (psiElement == null) return;
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return;

    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(myProject).getInjectionHost(psiFile);
    if (host != null) {
      psiFile = host.getContainingFile();
      if (psiFile == null) return;
    }

    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) return;
    if (myEditor == null || document != myEditor.getDocument()) {
      releaseEditor();
      removeAll();
      myEditor = createEditor(psiFile, document);
      if (myEditor == null) return;
      myLineHeight = myEditor.getLineHeight();
      myEditor.setBorder(null);
      add(myEditor.getComponent(), BorderLayout.CENTER);

      invalidate();
      validate();
    }

    if (!Comparing.equal(infos, myCachedSelectedUsageInfos) // avoid moving viewport
        || !UsageViewPresentation.arePatternsEqual(myCachedSearchPattern, myPresentation.getSearchPattern())
        || !UsageViewPresentation.arePatternsEqual(myCachedReplacePattern, myPresentation.getReplacePattern())
      ) {
      highlight(infos, myEditor, myProject, true, HighlighterLayer.ADDITIONAL_SYNTAX);
      myCachedSelectedUsageInfos = infos;
      myCachedSearchPattern = myPresentation.getSearchPattern();
      myCachedReplacePattern = myPresentation.getReplacePattern();
    }
  }

  public int getLineHeight() {
    return myLineHeight;
  }


  private static final Key<Boolean> IN_PREVIEW_USAGE_FLAG = Key.create("IN_PREVIEW_USAGE_FLAG");

  public static void highlight(@NotNull final List<UsageInfo> infos,
                               @NotNull final Editor editor,
                               @NotNull final Project project,
                               boolean highlightOnlyNameElements,
                               int highlightLayer) {
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());

    MarkupModel markupModel = editor.getMarkupModel();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getUserData(IN_PREVIEW_USAGE_FLAG) != null) {
        highlighter.dispose();
      }
    }
    Balloon balloon = editor.getUserData(REPLACEMENT_BALLOON_KEY);
    if (balloon != null && !Disposer.isDisposed(balloon)) {
      Disposer.dispose(balloon);
      editor.putUserData(REPLACEMENT_BALLOON_KEY, null);
    }
    FindModel findModel = getFindModel(editor);
    for (int i = infos.size()-1; i>=0; i--) { // finish with the first usage so that caret end up there
      UsageInfo info = infos.get(i);
      PsiElement psiElement = info.getElement();
      if (psiElement == null || !psiElement.isValid()) continue;
      int offsetInFile = psiElement.getTextOffset();

      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      TextRange elementRange = psiElement.getTextRange();
      TextRange infoRange = info.getRangeInElement();
      TextRange textRange = infoRange == null 
                            || infoRange.getStartOffset() > elementRange.getLength() 
                            || infoRange.getEndOffset() > elementRange.getLength() ? null 
                                                                                   : elementRange.cutOut(infoRange);
      if (textRange == null) textRange = elementRange;
      // hack to determine element range to highlight
      if (highlightOnlyNameElements && psiElement instanceof PsiNamedElement && !(psiElement instanceof PsiFile)) {
        PsiFile psiFile = psiElement.getContainingFile();
        PsiElement nameElement = psiFile.findElementAt(offsetInFile);
        if (nameElement != null) {
          textRange = nameElement.getTextRange();
        }
      }
      // highlight injected element in host document textrange
      textRange = InjectedLanguageManager.getInstance(project).injectedToHost(psiElement, textRange);

      RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                                   highlightLayer, attributes,
                                                                                   HighlighterTargetArea.EXACT_RANGE);
      highlighter.putUserData(IN_PREVIEW_USAGE_FLAG, Boolean.TRUE);
      if (infoRange != null && findModel != null && findModel.isReplaceState()) {
        RangeHighlighter boxHighlighter
          = markupModel.addRangeHighlighter(
          infoRange.getStartOffset(),
          infoRange.getEndOffset(),
          highlightLayer,
          new TextAttributes(null, null, editor.getColorsScheme().getColor(EditorColors.CARET_COLOR), EffectType.BOXED, Font.PLAIN),
          HighlighterTargetArea.EXACT_RANGE);
        boxHighlighter.putUserData(IN_PREVIEW_USAGE_FLAG, Boolean.TRUE);
        editor.getCaretModel().moveToOffset(infoRange.getEndOffset());
      } else {
        editor.getCaretModel().moveToOffset(textRange.getEndOffset());
      }

      if (findModel != null && infos.size() == 1 && infoRange != null && infoRange.equals(textRange)) {
        showBalloon(project, editor, infoRange, findModel);
      }
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }
  private static Key<Balloon> REPLACEMENT_BALLOON_KEY = Key.create("REPLACEMENT_BALLOON_KEY");

  private static void showBalloon(Project project, Editor editor, TextRange range, @NotNull FindModel findModel) {

    try {
      String replacementPreviewText = FindManager.getInstance(project)
        .getStringToReplace(editor.getDocument().getText(range), findModel, range.getStartOffset(), editor.getDocument().getText());
      ReplacementView replacementView = new ReplacementView(replacementPreviewText);

      BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(replacementView);
      balloonBuilder.setFadeoutTime(0);
      balloonBuilder.setFillColor(IdeTooltipManager.GRAPHITE_COLOR);
      balloonBuilder.setAnimationCycle(0);
      balloonBuilder.setHideOnClickOutside(false);
      balloonBuilder.setHideOnKeyOutside(false);
      balloonBuilder.setHideOnAction(false);
      balloonBuilder.setCloseButtonEnabled(true);
      Balloon balloon = balloonBuilder.createBalloon();
      EditorUtil.disposeWithEditor(editor, balloon);

      balloon.show(new ReplacementBalloonPositionTracker(project, editor, range, findModel), Balloon.Position.below);
      editor.putUserData(REPLACEMENT_BALLOON_KEY, balloon);

    }
    catch (FindManager.MalformedReplacementStringException e) {
      //Not a problem, just don't show balloon in this case
    }
  }

  @Nullable
  private static FindModel getFindModel(@NotNull Editor editor) {
    UsagePreviewPanel panel = editor.getUserData(PREVIEW_EDITOR_FLAG);
    Pattern searchPattern = null;
    Pattern replacePattern = null;
    if (panel != null) {
      searchPattern = panel.myPresentation.getSearchPattern();
      replacePattern = panel.myPresentation.getReplacePattern();
    }

    if (searchPattern == null || replacePattern == null) return null;
    FindModel stub = new FindModel();
    stub.setMultiline(true);
    stub.setRegularExpressions(true);
    stub.setReplaceAll(true);
    stub.setStringToFind(searchPattern.pattern());
    stub.setStringToReplace(replacePattern.pattern());
    return stub;
  }

  private static final Key<UsagePreviewPanel> PREVIEW_EDITOR_FLAG = Key.create("PREVIEW_EDITOR_FLAG");
  private Editor createEditor(final PsiFile psiFile, Document document) {
    if (isDisposed) return null;
    Project project = psiFile.getProject();

    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getVirtualFile(), !myIsEditor, EditorKind.PREVIEW);

    EditorSettings settings = editor.getSettings();
    customizeEditorSettings(settings);

    editor.putUserData(PREVIEW_EDITOR_FLAG, this);
    return editor;
  }

  protected void customizeEditorSettings(EditorSettings settings) {
    settings.setLineMarkerAreaShown(myIsEditor);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setAnimatedScrolling(false);
  }

  @Override
  public void dispose() {
    isDisposed = true;
    releaseEditor();
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getProject() == myProject && editor.getUserData(PREVIEW_EDITOR_FLAG) == this) {
        LOG.error("Editor was not released:"+editor);
      }
    }
  }

  private void releaseEditor() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
      myCachedSelectedUsageInfos = null;
      myCachedSearchPattern = null;
      myCachedReplacePattern = null;
    }
  }

  @Nullable
  public final String getCannotPreviewMessage(@Nullable final List<UsageInfo> infos) {
    if (infos == null || infos.isEmpty()) {
      return UsageViewBundle.message("select.the.usage.to.preview", myPresentation.getUsagesWord());
    } else {
      PsiFile psiFile = null;
      for (UsageInfo info : infos) {
        PsiElement element = info.getElement();
        if (element == null) continue;
        PsiFile file = element.getContainingFile();
        if (psiFile == null) {
          psiFile = file;
        } else {
          if (psiFile != file) {
            return UsageViewBundle.message("several.occurrences.selected");
          }
        }
      }
    }
    return null;
  }

  @Override
  public void updateLayoutLater(@Nullable final List<UsageInfo> infos) {
    String cannotPreviewMessage = getCannotPreviewMessage(infos);
    if (cannotPreviewMessage != null) {
      releaseEditor();
      removeAll();
      int newLineIndex = cannotPreviewMessage.indexOf("\n");
      if (newLineIndex == -1) {
        getEmptyText().setText(cannotPreviewMessage);
      } else {
        getEmptyText()
          .setText(cannotPreviewMessage.substring(0, newLineIndex))
          .appendSecondaryText(cannotPreviewMessage.substring(newLineIndex+1), StatusText.DEFAULT_ATTRIBUTES, null);
      }
      revalidate();
    }
    else {
      resetEditor(infos);
    }
  }

  private static class ReplacementView extends JPanel {
    private static final String MALFORMED_REPLACEMENT_STRING = "Malformed replacement string";

    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
    }

    public ReplacementView(@Nullable String replacement) {
      String textToShow = replacement;
      if (replacement == null) {
        textToShow = MALFORMED_REPLACEMENT_STRING;
      }
      JLabel jLabel = new JLabel(textToShow);
      jLabel.setForeground(replacement != null ? new JBColor(Gray._240, Gray._200) : JBColor.RED);
      add(jLabel);
    }
  }

  private static class ReplacementBalloonPositionTracker extends PositionTracker<Balloon> {
    private final Project myProject;
    private final Editor myEditor;
    private final TextRange myRange;
    private final FindModel myFindModel;

     ReplacementBalloonPositionTracker(Project project, Editor editor, TextRange range, FindModel findModel) {
      super(editor.getContentComponent());
      myProject = project;
      myEditor = editor;
      myRange = range;
      myFindModel = findModel;
    }

    @Override
    public RelativePoint recalculateLocation(final Balloon balloon) {
      int startOffset = myRange.getStartOffset();
      int endOffset = myRange.getEndOffset();

      if (!insideVisibleArea(myEditor, myRange)) {
        if (!balloon.isDisposed()) {
          Disposer.dispose(balloon);
        }

        VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
          @Override
          public void visibleAreaChanged(VisibleAreaEvent e) {
            if (insideVisibleArea(myEditor, myRange)) {
              showBalloon(myProject, myEditor, myRange, myFindModel);
              final VisibleAreaListener visibleAreaListener = this;
                myEditor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
            }
          }
        };
        myEditor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
      }

      Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
      Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
      Point point = new Point((startPoint.x + endPoint.x)/2, startPoint.y + myEditor.getLineHeight());

      return new RelativePoint(myEditor.getContentComponent(), point);
    }
  }

  static boolean insideVisibleArea(Editor e, TextRange r) {
    int textLength = e.getDocument().getTextLength();
    if (r.getStartOffset() > textLength) return false;
    if (r.getEndOffset() > textLength) return false;
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }

}

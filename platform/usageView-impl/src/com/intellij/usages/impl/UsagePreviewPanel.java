// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
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
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class UsagePreviewPanel extends UsageContextPanelBase implements DataProvider {
  public static final String LINE_HEIGHT_PROPERTY = "UsageViewPanel.lineHeightProperty";

  private static final Logger LOG = Logger.getInstance(UsagePreviewPanel.class);
  private Editor myEditor;
  private final boolean myIsEditor;
  private int myLineHeight;
  private List<? extends UsageInfo> myCachedSelectedUsageInfos;
  private Pattern myCachedSearchPattern;
  private Pattern myCachedReplacePattern;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

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
  public Object getData(@NotNull @NonNls String dataId) {
    if (CommonDataKeys.EDITOR.is(dataId) && myEditor != null) {
      return myEditor;
    }
    if (Registry.is("ide.find.preview.navigate.to.caret") && CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId) && myEditor instanceof EditorEx) {
      LogicalPosition position = myEditor.getCaretModel().getLogicalPosition();
      VirtualFile file = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
      if (file != null) {
        return new Navigatable[]{new OpenFileDescriptor(myProject, file, position.line, position.column)};
      }
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
      return UsageViewBundle.message("tab.title.preview");
    }
  }

  private void resetEditor(@NotNull final List<? extends UsageInfo> infos) {
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
      if (isDisposed) return;
      myEditor = createEditor(psiFile, document);
      setLineHeight(myEditor.getLineHeight());
      myEditor.setBorder(null);
      add(myEditor.getComponent(), BorderLayout.CENTER);

      invalidate();
      validate();
    }

    if (!Comparing.equal(infos, myCachedSelectedUsageInfos) // avoid moving viewport
        || !UsageViewPresentation.arePatternsEqual(myCachedSearchPattern, myPresentation.getSearchPattern())
        || !UsageViewPresentation.arePatternsEqual(myCachedReplacePattern, myPresentation.getReplacePattern())) {
      highlight(infos, myEditor, myProject, true, HighlighterLayer.ADDITIONAL_SYNTAX);
      myCachedSelectedUsageInfos = infos;
      myCachedSearchPattern = myPresentation.getSearchPattern();
      myCachedReplacePattern = myPresentation.getReplacePattern();
    }
  }

  private void setLineHeight(int lineHeight) {
    if (lineHeight != myLineHeight) {
      int oldHeight = myLineHeight;
      myLineHeight = lineHeight;
      myPropertyChangeSupport.firePropertyChange(LINE_HEIGHT_PROPERTY, oldHeight, myLineHeight);
    }
  }

  public int getLineHeight() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLineHeight;
  }

  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(propertyName, listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  private static final Key<Boolean> IN_PREVIEW_USAGE_FLAG = Key.create("IN_PREVIEW_USAGE_FLAG");

  public static void highlight(@NotNull final List<? extends UsageInfo> infos,
                               @NotNull final Editor editor,
                               @NotNull final Project project,
                               boolean highlightOnlyNameElements,
                               int highlightLayer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(PsiDocumentManager.getInstance(project).isCommitted(editor.getDocument()));

    MarkupModel markupModel = editor.getMarkupModel();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getUserData(IN_PREVIEW_USAGE_FLAG) != null) {
        highlighter.dispose();
      }
    }
    Balloon balloon = editor.getUserData(REPLACEMENT_BALLOON_KEY);
    if (balloon != null && !balloon.isDisposed()) {
      Disposer.dispose(balloon);
      editor.putUserData(REPLACEMENT_BALLOON_KEY, null);
    }
    FindModel findModel = getReplacementModel(editor);
    for (int i = infos.size() - 1; i >= 0; i--) { // finish with the first usage so that caret end up there
      UsageInfo info = infos.get(i);
      PsiElement psiElement = info.getElement();
      if (psiElement == null || !psiElement.isValid()) continue;
      int offsetInFile = psiElement.getTextOffset();

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

      RangeHighlighter highlighter = markupModel.addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES,
                                                                     textRange.getStartOffset(),
                                                                     textRange.getEndOffset(),
                                                                     highlightLayer,
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
      }
      else {
        editor.getCaretModel().moveToOffset(textRange.getEndOffset());
      }

      if (findModel != null && infos.size() == 1 && infoRange != null && infoRange.equals(textRange)) {
        showBalloon(project, editor, infoRange, findModel);
      }
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  private static final Key<Balloon> REPLACEMENT_BALLOON_KEY = Key.create("REPLACEMENT_BALLOON_KEY");

  private static void showBalloon(@NotNull Project project, @NotNull Editor editor, @NotNull TextRange range, @NotNull FindModel findModel) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    try {
      String replacementPreviewText = FindManager.getInstance(project)
        .getStringToReplace(editor.getDocument().getText(range), findModel, range.getStartOffset(),
                            editor.getDocument().getText());
      if (!Registry.is("ide.find.show.replacement.hint.for.simple.regexp")
          && Objects.equals(replacementPreviewText, findModel.getStringToReplace())) {
        return;
      }
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
  private static FindModel getReplacementModel(@NotNull Editor editor) {
    UsagePreviewPanel panel = editor.getUserData(PREVIEW_EDITOR_FLAG);
    Pattern searchPattern = null;
    Pattern replacePattern = null;
    if (panel != null) {
      searchPattern = panel.myPresentation.getSearchPattern();
      replacePattern = panel.myPresentation.getReplacePattern();
    }

    if (searchPattern == null || replacePattern == null) {
      return null;
    }
    FindModel stub = new FindModel();
    stub.setMultiline(true);
    stub.setRegularExpressions(true);
    stub.setReplaceAll(true);
    stub.setStringToFind(searchPattern.pattern());
    stub.setStringToReplace(replacePattern.pattern());
    return stub;
  }

  private static final Key<UsagePreviewPanel> PREVIEW_EDITOR_FLAG = Key.create("PREVIEW_EDITOR_FLAG");

  @NotNull
  private Editor createEditor(@NotNull PsiFile psiFile, @NotNull Document document) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Creating preview for " + psiFile.getVirtualFile());
    }
    Project project = psiFile.getProject();

    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getVirtualFile(), !myIsEditor, EditorKind.PREVIEW);

    EditorSettings settings = editor.getSettings();
    customizeEditorSettings(settings);

    editor.putUserData(PREVIEW_EDITOR_FLAG, this);
    return editor;
  }

  private void customizeEditorSettings(@NotNull EditorSettings settings) {
    settings.setLineMarkerAreaShown(myIsEditor);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setAnimatedScrolling(false);
    settings.setAutoCodeFoldingEnabled(false);
  }

  @Override
  public void dispose() {
    isDisposed = true;
    releaseEditor();
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getProject() == myProject && editor.getUserData(PREVIEW_EDITOR_FLAG) == this) {
        LOG.error("Editor was not released:" + editor);
      }
    }
  }

  private void releaseEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
      myCachedSelectedUsageInfos = null;
      myCachedSearchPattern = null;
      myCachedReplacePattern = null;
    }
  }

  @Nullable
  public String getCannotPreviewMessage(@Nullable final List<? extends UsageInfo> infos) {
    return cannotPreviewMessage(infos);
  }

  @Nullable
  private @NlsContexts.StatusText
  static String cannotPreviewMessage(@Nullable List<? extends UsageInfo> infos) {
    if (infos == null || infos.isEmpty()) {
      return UsageViewBundle.message("select.the.usage.to.preview");
    }
    PsiFile psiFile = null;
    for (UsageInfo info : infos) {
      PsiFile file = info.getFile();
      if (psiFile == null) {
        psiFile = file;
      }
      else {
        if (psiFile != file) {
          return UsageViewBundle.message("several.occurrences.selected");
        }
      }
    }
    return null;
  }

  @Override
  public void updateLayoutLater(@Nullable final List<? extends UsageInfo> infos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String cannotPreviewMessage = cannotPreviewMessage(infos);
    if (cannotPreviewMessage == null) {
      resetEditor(infos);
    }
    else {
      releaseEditor();
      removeAll();
      int newLineIndex = cannotPreviewMessage.indexOf('\n');
      if (newLineIndex == -1) {
        getEmptyText().setText(cannotPreviewMessage);
      }
      else {
        getEmptyText()
          .setText(cannotPreviewMessage.substring(0, newLineIndex))
          .appendSecondaryText(cannotPreviewMessage.substring(newLineIndex + 1), StatusText.DEFAULT_ATTRIBUTES, null);
      }
      revalidate();
    }
  }

  private static class ReplacementView extends JPanel {
    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
    }

    ReplacementView(@Nullable @NlsSafe String replacement) {
      String textToShow = replacement;
      if (replacement == null) {
        textToShow = UsageViewBundle.message("label.malformed.replacement.string");
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

    ReplacementBalloonPositionTracker(@NotNull Project project, @NotNull Editor editor, @NotNull TextRange range, @NotNull FindModel findModel) {
      super(editor.getContentComponent());
      myProject = project;
      myEditor = editor;
      myRange = range;
      myFindModel = findModel;
    }

    @Override
    public RelativePoint recalculateLocation(final @NotNull Balloon balloon) {
      int startOffset = myRange.getStartOffset();
      int endOffset = myRange.getEndOffset();

      if (!insideVisibleArea(myEditor, myRange)) {
        if (!balloon.isDisposed()) {
          Disposer.dispose(balloon);
        }

        VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
          @Override
          public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
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
      Point point = new Point((startPoint.x + endPoint.x) / 2, startPoint.y + myEditor.getLineHeight());

      return new RelativePoint(myEditor.getContentComponent(), point);
    }
  }

  private static boolean insideVisibleArea(@NotNull Editor e, @NotNull TextRange r) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int textLength = e.getDocument().getTextLength();
    if (r.getStartOffset() > textLength) return false;
    if (r.getEndOffset() > textLength) return false;
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }
}

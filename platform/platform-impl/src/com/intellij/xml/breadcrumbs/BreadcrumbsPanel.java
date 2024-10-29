// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.ui.RelativeFont.SMALL;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;

public abstract class BreadcrumbsPanel extends JComponent implements Disposable {
  private static final Logger LOG = getInstance(BreadcrumbsPanel.class);

  final PsiBreadcrumbs breadcrumbs = new PsiBreadcrumbs();

  protected final Project myProject;
  protected Editor myEditor;
  private Collection<RangeHighlighter> myHighlighed;
  protected boolean myUserCaretChange = true;
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, breadcrumbs);

  private final List<BreadcrumbListener> myBreadcrumbListeners = new ArrayList<>();

  private final Update myUpdate = new Update(this) {
    @Override
    public void run() {
      updateCrumbsAsync();
    }

    @Override
    public boolean canEat(final @NotNull Update update) {
      return true;
    }
  };

  private static final Key<BreadcrumbsPanel> BREADCRUMBS_COMPONENT_KEY = new Key<>("BREADCRUMBS_KEY");
  private static final Iterable<? extends Crumb> EMPTY_BREADCRUMBS = Collections.emptyList();

  public BreadcrumbsPanel(final @NotNull Editor editor) {
    myEditor = editor;
    putBreadcrumbsComponent(myEditor, this);

    final Project project = editor.getProject();
    assert project != null;
    myProject = project;

    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        queueUpdate();
      }
    }, this);

    if (ClientId.isLocal(ClientEditorManager.getClientId(myEditor))) {
      attachEditorListeners(editor);

      breadcrumbs.onHover(this::itemHovered);
      breadcrumbs.onSelect(this::itemSelected);
      breadcrumbs.setFont(getNewFont(myEditor));

      JScrollPane pane = createScrollPane(breadcrumbs, true);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      pane.getHorizontalScrollBar().setEnabled(false);
      setLayout(new BorderLayout());
      add(BorderLayout.CENTER, pane);

      Disposer.register(this, UiNotifyConnector.installOn(breadcrumbs, myQueue));
    }

    Disposer.register(this, myQueue);

    BreadcrumbsProvider.EP_NAME.addChangeListener(() -> updateCrumbsSync(), this);
    BreadcrumbsPresentationProvider.EP_NAME.addChangeListener(() -> updateCrumbsSync(), this);

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myQueue.setPassThrough(true);
    }

    queueUpdate();
  }

  private void attachEditorListeners(final @NotNull Editor editor) {
    if (editor instanceof EditorEx) {
      ((EditorEx)editor).addPropertyChangeListener(this::updateEditorFont, this);
    }

    final CaretListener caretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(final @NotNull CaretEvent e) {
        if (myUserCaretChange) {
          queueUpdate();
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener, this);

    EditorGutter gutter = editor.getGutter();
    if (gutter instanceof EditorGutterComponentEx gutterComponent) {
      if (!(gutterComponent instanceof MouseListener)) {
        LOG.error("Can't delegate mouse events to EditorGutterComponentEx: " + gutterComponent);
      }

      MouseEventAdapter<EditorGutterComponentEx> mouseListener = new MouseEventAdapter<>(gutterComponent) {
        @Override
        protected @NotNull MouseEvent convert(@NotNull MouseEvent event) {
          return convert(event, gutterComponent);
        }

        @Override
        protected MouseListener getMouseListener(@NotNull EditorGutterComponentEx adapter) {
          if (adapter instanceof MouseListener && adapter.isShowing()) {
            return (MouseListener)adapter;
          }
          return null;
        }
      };
      ComponentAdapter resizeListener = new ComponentAdapter() {
        @DirtyUI
        @Override
        public void componentResized(ComponentEvent event) {
          breadcrumbs.setFont(getNewFont(myEditor));
        }
      };

      addComponentListener(resizeListener);
      gutterComponent.addComponentListener(resizeListener);
      breadcrumbs.addMouseListener(mouseListener);
      Disposer.register(this, () -> {
        removeComponentListener(resizeListener);
        gutterComponent.removeComponentListener(resizeListener);
        breadcrumbs.removeMouseListener(mouseListener);
      });
    }
  }

  public Breadcrumbs getBreadcrumbs() {
    return breadcrumbs;
  }

  protected int getLeftOffset() {
    EditorGutterComponentEx gutter = ((EditorGutterComponentEx)myEditor.getGutter());
    return gutter.getWhitespaceSeparatorOffset();
  }

  private void updateCrumbsAsync() {
    if (myEditor == null || myEditor.isDisposed()) return;

    ReadAction
      .nonBlocking(() -> computeCrumbs(myEditor.getCaretModel().getOffset()))
      .withDocumentsCommitted(myProject)
      .expireWith(this)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.any(), crumbs -> applyCrumbs(crumbs))
      .submit(NonUrgentExecutor.getInstance());
  }

  private void applyCrumbs(Iterable<? extends Crumb> _crumbs) {
    boolean areCrumbsVisible = breadcrumbs.isShowing() ||
                               !ClientId.isLocal(ClientEditorManager.getClientId(myEditor)) ||
                               ApplicationManager.getApplication().isHeadlessEnvironment();
    Iterable<? extends Crumb> crumbs = _crumbs != null && areCrumbsVisible ? _crumbs : EMPTY_BREADCRUMBS;
    breadcrumbs.setFont(getNewFont(myEditor));
    breadcrumbs.setCrumbs(crumbs);
    notifyListeners(crumbs);
  }

  private void updateCrumbsSync() {
    if (myEditor == null || myEditor.isDisposed()) return;
    int offset = myEditor.getCaretModel().getOffset();

    Iterable<? extends Crumb> crumbs = computeCrumbs(offset);
    applyCrumbs(crumbs);
  }

  public void queueUpdate() {
    myQueue.cancelAllUpdates();
    myQueue.queue(myUpdate);
  }

  public void addBreadcrumbListener(BreadcrumbListener listener, Disposable parentDisposable) {
    myBreadcrumbListeners.add(listener);
    Disposer.register(parentDisposable, () -> myBreadcrumbListeners.remove(listener));
  }

  private void notifyListeners(@NotNull Iterable<? extends Crumb> breadcrumbs) {
    for (BreadcrumbListener listener : myBreadcrumbListeners) {
      listener.breadcrumbsChanged(breadcrumbs);
    }
  }

  private void itemSelected(Crumb crumb, InputEvent event) {
    if (event == null) return;
    navigateToCrumb(crumb, event.isShiftDown() || event.isMetaDown());
  }

  private void itemHovered(Crumb crumb, @SuppressWarnings("unused") InputEvent event) {
    if (!Registry.is("editor.breadcrumbs.highlight.on.hover")) {
      return;
    }

    HighlightManager hm = HighlightManager.getInstance(myProject);
    if (myHighlighed != null) {
      for (RangeHighlighter highlighter : myHighlighed) {
        hm.removeSegmentHighlighter(myEditor, highlighter);
      }
      myHighlighed = null;
    }

    CrumbHighlightInfo info = getHighlightInfo(crumb);

    if (info != null) {
      final TextRange range = info.range;
      final TextAttributes attributes = new TextAttributes();
      Color color = info.presentation != null ? info.presentation.getBackgroundColor(false, false, false) : null;
      if (color == null) color = BreadcrumbsComponent.ButtonSettings.getBackgroundColor(false, false, false, false);
      if (color == null) color = UIUtil.getLabelBackground();
      final Color background = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.CARET_ROW_COLOR);
      attributes.setBackgroundColor(UIUtil.makeTransparent(color, background != null ? background : Gray._200, 0.3));
      myHighlighed = new ArrayList<>(1);
      int flags = HighlightManager.HIDE_BY_ESCAPE | HighlightManager.HIDE_BY_TEXT_CHANGE | HighlightManager.HIDE_BY_ANY_KEY;
      hm.addOccurrenceHighlight(myEditor, range.getStartOffset(), range.getEndOffset(), attributes, flags, myHighlighed, null);
    }
  }

  protected abstract @Nullable Iterable<? extends Crumb> computeCrumbs(int offset);

  protected void navigateToCrumb(Crumb crumb, boolean withSelection) {
    if (crumb instanceof NavigatableCrumb navigatableCrumb) {
      myUserCaretChange = false;
      navigatableCrumb.navigate(myEditor, withSelection);
    }
  }

  protected @Nullable CrumbHighlightInfo getHighlightInfo(Crumb crumb) {
    if (crumb instanceof NavigatableCrumb) {
      final TextRange range = ((NavigatableCrumb)crumb).getHighlightRange();
      if (range == null) return null;
      final CrumbPresentation p = PsiCrumb.getPresentation(crumb);
      return new CrumbHighlightInfo(range, p);
    }
    return null;
  }

  protected static final class CrumbHighlightInfo {
    public final @NotNull TextRange range;
    public final @Nullable CrumbPresentation presentation;

    public CrumbHighlightInfo(@NotNull TextRange range, @Nullable CrumbPresentation presentation) {
      this.range = range;
      this.presentation = presentation;
    }
  }

  private static void putBreadcrumbsComponent(@NotNull Editor editor, @NotNull BreadcrumbsPanel panel) {
    BreadcrumbsPanel oldPanel = editor.getUserData(BREADCRUMBS_COMPONENT_KEY);
    if (oldPanel != null) {
      LOG.error("Multiple breadcrumbs panels registered for the same Editor, old panel: " + oldPanel, new Throwable());
    }
    editor.putUserData(BREADCRUMBS_COMPONENT_KEY, panel);
  }

  public static @Nullable BreadcrumbsPanel getBreadcrumbsComponent(@NotNull Editor editor) {
    return editor.getUserData(BREADCRUMBS_COMPONENT_KEY);
  }

  @Override
  public void dispose() {
    if (myEditor != null) {
      myEditor.putUserData(BREADCRUMBS_COMPONENT_KEY, null);
    }
    myEditor = null;
    breadcrumbs.setCrumbs(EMPTY_BREADCRUMBS);
    notifyListeners(EMPTY_BREADCRUMBS);
  }

  private void updateEditorFont(PropertyChangeEvent event) {
    if (EditorEx.PROP_FONT_SIZE.equals(event.getPropertyName())) queueUpdate();
  }

  private static Font getNewFont(Editor editor) {
    Font font = editor == null || Registry.is("editor.breadcrumbs.system.font") ? StartupUiUtil.getLabelFont() : getEditorFont(editor);
    return UISettings.getInstance().getUseSmallLabelsOnTabs() && !ExperimentalUI.isNewUI() ? SMALL.derive(font) : font;
  }

  private static Font getEditorFont(Editor editor) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay('a', Font.PLAIN, editor.getColorsScheme().getFontPreferences(),
                                                           null).getFont();
  }

  protected static @Nullable FileBreadcrumbsCollector findCollectorFor(@NotNull Project project,
                                                                       @Nullable VirtualFile file,
                                                                       @NotNull BreadcrumbsPanel panel) {
    if (file == null) return null;
    FileBreadcrumbsCollector collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, file);
    collector.watchForChanges(file, panel.myEditor, panel, () -> panel.queueUpdate());
    return collector;
  }
}

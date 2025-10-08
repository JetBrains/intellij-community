// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import java.awt.*;

public final class BreadcrumbsXmlWrapper extends BreadcrumbsPanel implements Border {
  private final @Nullable VirtualFile myFile;
  private final @NotNull FileBreadcrumbCollectorHolder myBreadcrumbsCollectorHolder = new FileBreadcrumbCollectorHolder();

  public BreadcrumbsXmlWrapper(final @NotNull Editor editor) {
    super(editor);
    myFile = FileDocumentManager.getInstance().getFile(myEditor.getDocument());

    if (myFile != null) {
      myBreadcrumbsCollectorHolder.update(myFile);
    }

    if (ExperimentalUI.isNewUI()) {
      putClientProperty(FileEditorManager.SEPARATOR_DISABLED, Boolean.TRUE);
    }
    setBorder(this);
  }

  private class FileBreadcrumbCollectorHolder {
    private volatile @Nullable FileBreadcrumbsCollector myBreadcrumbsCollector;
    private @NotNull Disposable myCollectorListenerDisposable = Disposer.newDisposable();

    public void update(@NotNull VirtualFile file) {
      FileBreadcrumbsCollector newCollector = FileBreadcrumbsCollector.findBreadcrumbsCollector(myProject, myFile);

      if (newCollector != myBreadcrumbsCollector) {
        Disposer.dispose(myCollectorListenerDisposable);
        if (newCollector != null) {
          Disposable newDisposable = Disposer.newDisposable(BreadcrumbsXmlWrapper.this);
          newCollector.watchForChanges(file, myEditor, newDisposable, () -> queueUpdate());
          myCollectorListenerDisposable = newDisposable;
        }
        else {
          myCollectorListenerDisposable = Disposer.newDisposable();
        }
        myBreadcrumbsCollector = newCollector;
      }
    }

    public @Nullable FileBreadcrumbsCollector getBreadcrumbsCollector() {
      return myBreadcrumbsCollector;
    }
  }

  @Override
  protected @Nullable Iterable<? extends Crumb> computeCrumbs(int offset) {
    if (myFile == null) return null;
    myBreadcrumbsCollectorHolder.update(myFile);
    var breadcrumbsCollector = myBreadcrumbsCollectorHolder.getBreadcrumbsCollector();
    if (breadcrumbsCollector == null) return null;

    Document document = myEditor.getDocument();
    Boolean forcedShown = BreadcrumbsForceShownSettings.getForcedShown(myEditor);
    return breadcrumbsCollector.computeCrumbs(myFile, document, offset, forcedShown);
  }

  public void navigate(NavigatableCrumb crumb, boolean withSelection) {
    myUserCaretChange = false;
    crumb.navigate(myEditor, withSelection);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(getBorderColor());
    RectanglePainter2D.DRAW.paint((Graphics2D)g, x, y, width, height, null, LinePainter2D.StrokeType.INSIDE,
                                  1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (ExperimentalUI.isNewUI()) {
      return breadcrumbs.above ? JBUI.insetsBottom(1) : JBUI.insetsTop(1);
    }
    return JBUI.emptyInsets();
  }

  private static Color getBorderColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.BREADCRUMBS_BORDER_COLOR);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  public static @Nullable BreadcrumbsXmlWrapper getBreadcrumbWrapper(@NotNull Editor editor) {
    Object obj = BreadcrumbsPanel.getBreadcrumbsComponent(editor);
    return obj instanceof BreadcrumbsXmlWrapper ? (BreadcrumbsXmlWrapper)obj : null;
  }

  @ApiStatus.Internal
  @Topic.AppLevel
  public static final Topic<Runnable> FORCE_RELOAD_BREADCRUMBS = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);
}

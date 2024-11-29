// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import java.awt.*;

public final class BreadcrumbsXmlWrapper extends BreadcrumbsPanel implements Border {
  private final VirtualFile myFile;
  private final FileBreadcrumbsCollector myBreadcrumbsCollector;

  public BreadcrumbsXmlWrapper(final @NotNull Editor editor) {
    super(editor);
    myFile = FileDocumentManager.getInstance().getFile(myEditor.getDocument());

    if (myFile != null) {
      myBreadcrumbsCollector = FileBreadcrumbsCollector.findBreadcrumbsCollector(myProject, myFile);
      myBreadcrumbsCollector.watchForChanges(myFile, myEditor, this, () -> queueUpdate());
    }
    else {
      myBreadcrumbsCollector = null;
    }

    if (ExperimentalUI.isNewUI()) {
      putClientProperty(FileEditorManager.SEPARATOR_DISABLED, Boolean.TRUE);
    }
    setBorder(this);
  }

  @Override
  protected @Nullable Iterable<? extends Crumb> computeCrumbs(int offset) {
    if (myBreadcrumbsCollector == null) return null;

    Document document = myEditor.getDocument();
    Boolean forcedShown = BreadcrumbsForceShownSettings.getForcedShown(myEditor);
    return myBreadcrumbsCollector.computeCrumbs(myFile, document, offset, forcedShown);
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
}

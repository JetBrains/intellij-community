// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper extends BreadcrumbsPanel {
  private final VirtualFile myFile;
  private final FileBreadcrumbsCollector myBreadcrumbsCollector;

  public BreadcrumbsXmlWrapper(@NotNull final Editor editor) {
    super(editor);
    myFile = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
    myBreadcrumbsCollector = findCollectorFor(myProject, myFile, this);
  }

  @Deprecated
  public JComponent getComponent() {
    return this;
  }

  @Nullable
  @Override
  protected Iterable<? extends Crumb> computeCrumbs(int offset) {
    if (myBreadcrumbsCollector == null) return null;

    Document document = myEditor.getDocument();
    Boolean forcedShown = BreadcrumbsForceShownSettings.getForcedShown(myEditor);
    return myBreadcrumbsCollector.computeCrumbs(myFile, document, offset, forcedShown);
  }

  public void navigate(NavigatableCrumb crumb, boolean withSelection) {
    myUserCaretChange = false;
    crumb.navigate(myEditor, withSelection);
  }

  @Nullable
  public static BreadcrumbsXmlWrapper getBreadcrumbsWrapper(@NotNull Editor editor) {
    return ObjectUtils.tryCast(BreadcrumbsPanel.getBreadcrumbsComponent(editor), BreadcrumbsXmlWrapper.class);
  }
}

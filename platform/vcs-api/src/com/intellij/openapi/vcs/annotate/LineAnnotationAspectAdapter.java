// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class LineAnnotationAspectAdapter implements LineAnnotationAspect, EditorGutterAction {
  private final String myId;
  private final boolean myShowByDefault;

  protected LineAnnotationAspectAdapter() {
    this(null, false);
  }

  protected LineAnnotationAspectAdapter(String id) {
    this(id, false);
  }

  public LineAnnotationAspectAdapter(String id, boolean showByDefault) {
    myId = id;
    myShowByDefault = showByDefault;
  }

  @Override
  @NlsContexts.Tooltip
  public String getTooltipText(int lineNumber) {
    return null;
  }

  @Override
  @NonNls
  public String getId() {
    return myId;
  }

  @Override
  public boolean isShowByDefault() {
    return myShowByDefault;
  }

  @Override
  public Cursor getCursor(final int lineNum) {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  @Override
  public void doAction(int lineNum) {
    showAffectedPaths(lineNum);
  }

  protected abstract void showAffectedPaths(int lineNum);
}

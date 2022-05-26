// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class LineAnnotationAspectAdapter implements LineAnnotationAspect, EditorGutterAction {
  @Nullable private final String myId;
  @NlsContexts.ListItem @Nullable private final String myDisplayName;
  private final boolean myShowByDefault;

  /**
   * @deprecated use {@link LineAnnotationAspectAdapter#LineAnnotationAspectAdapter(String, String, boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected LineAnnotationAspectAdapter() {
    this(null, null, false);
  }

  protected LineAnnotationAspectAdapter(@Nullable String id, @NlsContexts.ListItem @Nullable String displayName) {
    this(id, displayName, false);
  }

  /**
   * @deprecated use {@link LineAnnotationAspectAdapter#LineAnnotationAspectAdapter(String, String, boolean)}
   */
  @Deprecated
  public LineAnnotationAspectAdapter(@NonNls @Nullable String id, boolean showByDefault) {
    this(id, null, showByDefault);
  }

  public LineAnnotationAspectAdapter(@NonNls @Nullable String id,
                                     @NlsContexts.ListItem @Nullable String displayName,
                                     boolean showByDefault) {
    myId = id;
    myDisplayName = displayName;
    myShowByDefault = showByDefault;
  }

  @Override
  @NlsContexts.Tooltip
  public String getTooltipText(int lineNumber) {
    return null;
  }

  @Override
  public @NonNls @Nullable String getId() {
    return myId;
  }

  @Override
  public @NlsContexts.ListItem @Nullable String getDisplayName() {
    return myDisplayName != null ? myDisplayName : myId; //NON-NLS backward compatibility
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

  public static final LineAnnotationAspect NULL_ASPECT = new LineAnnotationAspectAdapter() {
    @Override
    protected void showAffectedPaths(int lineNum) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getValue(int line) {
      throw new UnsupportedOperationException();
    }
  };
}

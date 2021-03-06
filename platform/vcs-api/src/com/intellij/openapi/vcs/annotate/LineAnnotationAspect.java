// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Represents one part of a line annotation which is shown in the editor when the "Annotate"
 * action is invoked. Classes implementing this interface can also implement
 * {@link com.intellij.openapi.editor.EditorGutterAction} to handle clicks on the annotation.
 *
 * @author Konstantin Bulenkov
 * @see FileAnnotation#getAspects()
 */
public interface LineAnnotationAspect {
  @NonNls String AUTHOR = "Author";
  @NonNls String DATE = "Date";
  @NonNls String REVISION = "Revision";

  /**
   * Get annotation text for the specific line number
   *
   * @param line the line number to query
   * @return the annotation text
   */
  @NlsSafe
  String getValue(int line);

  /**
   * Used to show a tooltip for specific line or group of lines
   *
   * @param line the line number to query
   * @return the tooltip text for the line
   */
  @Nullable
  @NlsContexts.Tooltip
  String getTooltipText(int line);

  /**
   * Returns unique identifier, that will be used to show/hide some aspects
   * If {@code null} this line aspect won't be configurable in annotation settings
   *
   * @return unique id
   */
  @Nullable
  @NonNls
  String getId();

  /**
   * Returns {@code true} if this aspect will be shown on Annotate action
   *
   * @return {@code true} if this aspect will be shown on Annotate action
   */
  boolean isShowByDefault();

  /**
   * Used to override default text style
   */
  @Nullable
  default EditorFontType getStyle(int line) {
    return null;
  }

  /**
   * Used to override default text color
   */
  @Nullable
  default ColorKey getColor(int line) {
    return null;
  }

  /**
   * Used to override default background color
   */
  @Nullable
  default Color getBgColor(int line) {
    return null;
  }

  @NlsContexts.ListItem
  default String getDisplayName() {
    return getId(); //NON-NLS backward compatibility
  }
}

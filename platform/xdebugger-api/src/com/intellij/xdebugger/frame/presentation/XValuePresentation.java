// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame.presentation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Determines how a value is shown in debugger trees. Use one of the standard implementations (for {@link XStringValuePresentation strings},
 * {@link XNumericValuePresentation numbers}, {@link XKeywordValuePresentation keywords}
 * and for {@link XRegularValuePresentation other values}) or override this class if you need something special
 *
 * @see com.intellij.xdebugger.frame.XValueNode#setPresentation(javax.swing.Icon, XValuePresentation, boolean)
 */
public abstract class XValuePresentation {
  protected static final String DEFAULT_SEPARATOR = " = ";

  /**
   * Renders value text by delegating to {@code renderer} methods
   * @param renderer {@link XValueTextRenderer} instance which provides methods to
   */
  public abstract void renderValue(@NotNull XValueTextRenderer renderer);

  /**
   * @return separator between name and value in a debugger tree
   */
  @NotNull
  @NlsSafe
  public String getSeparator() {
    return DEFAULT_SEPARATOR;
  }

  /**
   * @return false if you do not want the name of the node before the separator
   */
  public boolean isShowName() {
    return true;
  }

  /**
   * @return optional type of the value, it is shown in gray color and surrounded by braces
   */
  @Nullable
  @NlsSafe
  public String getType() {
    return null;
  }

  public interface XValueTextRenderer {
    /**
     * Appends {@code value} with to the node text. Invisible characters are shown in escaped form.
     */
    void renderValue(@NotNull @NlsSafe String value);

    /**
     * Appends {@code value} surrounded by quotes to the node text colored as a string
     */
    void renderStringValue(@NotNull @NlsSafe String value);

    /**
     * Appends {@code value} highlighted as a number
     */
    void renderNumericValue(@NotNull @NlsSafe String value);

    /**
     * Appends {@code value} highlighted as a keyword
     */
    void renderKeywordValue(@NotNull @NlsSafe String value);

    void renderValue(@NotNull @NlsSafe String value, @NotNull TextAttributesKey key);

    /**
     * Appends {@code value} surrounded by quotes to the node text colored as a string
     *
     * @param value                             value to be shown
     * @param additionalSpecialCharsToHighlight characters which should be highlighted in a special color
     * @param maxLength                         maximum number of characters to show
     */
    void renderStringValue(@NotNull @NlsSafe String value, @Nullable @NlsSafe String additionalSpecialCharsToHighlight, int maxLength);

    /**
     * Appends gray colored {@code comment}
     */
    void renderComment(@NotNull @NlsSafe String comment);

    /**
     * Appends {@code symbol} which is not part of the value
     */
    void renderSpecialSymbol(@NotNull @NlsSafe String symbol);

    /**
     * Appends red colored {@code error}
     */
    void renderError(@NotNull @NlsSafe String error);
  }
}
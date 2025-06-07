// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public abstract class Comment {

  public static final Comment[] EMPTY_ARRAY = new Comment[0];

  public abstract @NlsSafe String getText();

  public abstract @Nullable @NlsSafe String getAuthor();

  public abstract @Nullable Date getDate();

  public void appendTo(StringBuilder builder) {
    builder.append("<hr>");
    if (getAuthor() != null) {
      builder.append("<b>Author:</b> ").append(getAuthor()).append("<br>");
    }
    if (getDate() != null) {
      builder.append("<b>Date:</b> ").append(DateFormatUtil.formatDateTime(getDate())).append("<br>");
    }
    builder.append(getText().replace("\n", "<br>")).append("<br>");
  }
}

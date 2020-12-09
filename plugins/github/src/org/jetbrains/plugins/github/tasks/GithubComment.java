// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.tasks;

import com.intellij.tasks.impl.SimpleComment;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.i18n.GithubBundle;

import java.util.Date;

final class GithubComment extends SimpleComment {
  @Nullable private final String myAvatarUrl;
  @NotNull private final String myUserHtmlUrl;

  public GithubComment(@Nullable Date date,
                       @Nullable String author,
                       @NotNull String text,
                       @Nullable String avatarUrl,
                       @NotNull String userHtmlUrl) {
    super(date, author, text);
    myAvatarUrl = avatarUrl;
    myUserHtmlUrl = userHtmlUrl;
  }

  @Override
  public void appendTo(StringBuilder builder) {
    builder.append("<hr>");
    builder.append("<table>");
    builder.append("<tr><td>");
    if (myAvatarUrl != null) {
      builder.append("<img src=\"").append(myAvatarUrl).append("\" height=\"40\" width=\"40\"/><br>");
    }
    builder.append("</td><td>");
    if (getAuthor() != null) {
      builder.append("<b>").append(GithubBundle.message("task.comment.author")).append("</b> <a href=\"").append(myUserHtmlUrl)
        .append("\">").append(getAuthor()).append("</a><br>");
    }
    if (getDate() != null) {
      builder.append("<b>").append(GithubBundle.message("task.comment.date")).append("</b> ")
        .append(DateFormatUtil.formatDateTime(getDate())).append("<br>");
    }
    builder.append("</td></tr></table>");

    builder.append(getText()).append("<br>");
  }
}

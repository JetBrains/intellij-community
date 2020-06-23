// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public final class IssueLinkHtmlRenderer {
  private IssueLinkHtmlRenderer() {
  }

  @NotNull
  public static String formatTextIntoHtml(@NotNull Project project, @NotNull String c) {
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), UIUtil.getLabelForeground(),
                                                         JBUI.CurrentTheme.Link.linkColor(), null) + "</head><body>" +
           formatTextWithLinks(project, c) + "</body></html>";
  }

  @NotNull
  public static String formatTextWithLinks(@NotNull Project project,
                                           @NotNull String str,
                                           @NotNull Convertor<? super String, String> convertor) {
    if (StringUtil.isEmpty(str)) return "";
    String comment = XmlStringUtil.escapeString(VcsUtil.trimCommitMessageToSaneSize(str), false);

    StringBuilder commentBuilder = new StringBuilder();
    IssueNavigationConfiguration config = IssueNavigationConfiguration.getInstance(project);
    final List<IssueNavigationConfiguration.LinkMatch> list = config.findIssueLinks(comment);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      TextRange range = match.getRange();
      commentBuilder.append(convertor.convert(comment.substring(pos, range.getStartOffset()))).append("<a href=\"").append(match.getTargetUrl()).append("\">");
      commentBuilder.append(range.substring(comment)).append("</a>");
      pos = range.getEndOffset();
    }
    commentBuilder.append(convertor.convert(comment.substring(pos)));
    comment = commentBuilder.toString();

    return comment.replace("\n", "<br>");
  }

  @NotNull
  public static String formatTextWithLinks(@NotNull Project project, @NotNull final String c) {
    return formatTextWithLinks(project, c, Convertor.self());
  }
}
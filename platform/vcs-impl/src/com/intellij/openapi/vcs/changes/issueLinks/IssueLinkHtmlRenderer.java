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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public final class IssueLinkHtmlRenderer {
  private IssueLinkHtmlRenderer() {
  }

  @Nls
  @NotNull
  public static String formatTextIntoHtml(@NotNull Project project, @NotNull @Nls String c) {
    // todo: use com.intellij.openapi.util.text.HtmlBuilder
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), UIUtil.getLabelForeground(), // NON-NLS
                                                         JBUI.CurrentTheme.Link.linkColor(), null) + "</head><body>" + // NON-NLS
           formatTextWithLinks(project, c) + "</body></html>"; // NON-NLS
  }

  @NotNull
  @Nls
  public static String formatTextWithLinks(@NotNull Project project,
                                           @NotNull @Nls String str,
                                           @NotNull Convertor<@Nls ? super String, @Nls String> convertor) {
    if (StringUtil.isEmpty(str)) return "";
    String comment = XmlStringUtil.escapeString(VcsUtil.trimCommitMessageToSaneSize(str), false);

    @Nls StringBuilder commentBuilder = new StringBuilder();
    IssueNavigationConfiguration config = IssueNavigationConfiguration.getInstance(project);
    final List<IssueNavigationConfiguration.LinkMatch> list = config.findIssueLinks(comment);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      TextRange range = match.getRange();
      commentBuilder.append(convertor.convert(comment.substring(pos, range.getStartOffset()))).append("<a href=\"").append(match.getTargetUrl()).append("\">"); // NON-NLS
      commentBuilder.append(range.substring(comment)).append("</a>"); // NON-NLS
      pos = range.getEndOffset();
    }
    commentBuilder.append(convertor.convert(comment.substring(pos)));
    comment = commentBuilder.toString();

    return comment.replace("\n", UIUtil.BR);
  }

  @NotNull
  @Nls
  public static String formatTextWithLinks(@NotNull Project project, @NotNull @Nls String c) {
    return formatTextWithLinks(project, c, Convertor.self());
  }
}
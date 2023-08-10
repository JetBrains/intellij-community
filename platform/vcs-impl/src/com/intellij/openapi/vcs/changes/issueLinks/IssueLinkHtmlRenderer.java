// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


public final class IssueLinkHtmlRenderer {
  private IssueLinkHtmlRenderer() {
  }

  /**
   * WARNING: hard codes current LaF into the text foreground.
   */
  @Nls
  @NotNull
  public static String formatTextIntoHtml(@NotNull Project project, @NotNull @Nls String c) {
    // todo: use com.intellij.openapi.util.text.HtmlBuilder
    return "<html><head>" + UIUtil.getCssFontDeclaration(StartupUiUtil.getLabelFont(), UIUtil.getLabelForeground(), // NON-NLS
                                                         JBUI.CurrentTheme.Link.Foreground.ENABLED, null) + "</head><body>" + // NON-NLS
           formatTextWithLinks(project, c) + "</body></html>"; // NON-NLS
  }

  @NotNull
  @Nls
  public static String formatTextWithLinks(@NotNull Project project,
                                           @NotNull @Nls String str,
                                           @NotNull Convertor<@Nls ? super String, @Nls String> convertor) {
    if (StringUtil.isEmpty(str)) return "";
    @Nls StringBuilder commentBuilder = new StringBuilder();
    String comment = XmlStringUtil.escapeString(VcsUtil.trimCommitMessageToSaneSize(str), false);
    IssueNavigationConfiguration.processTextWithLinks(comment, IssueNavigationConfiguration.getInstance(project).findIssueLinks(comment),
                                                      s -> commentBuilder.append(convertor.convert(s)),
                                                      (text, target) -> {
                                                        commentBuilder
                                                          .append("<a href=\"")
                                                          .append(target).append("\">")
                                                          .append(text).append("</a>");
                                                      });
    return commentBuilder.toString().replace("\n", UIUtil.BR);
  }

  @NotNull
  @Nls
  public static String formatTextWithLinks(@NotNull Project project, @NotNull @Nls String c) {
    return formatTextWithLinks(project, c, Convertor.self());
  }
}
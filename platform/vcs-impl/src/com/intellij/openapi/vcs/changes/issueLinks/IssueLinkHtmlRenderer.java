/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class IssueLinkHtmlRenderer {
  private IssueLinkHtmlRenderer() {
  }

  @NotNull
  public static String formatTextIntoHtml(@NotNull Project project, @NotNull String c) {
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" +
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
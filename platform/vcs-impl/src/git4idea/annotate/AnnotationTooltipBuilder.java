// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotationTooltipBuilder {
  @NotNull private final Project myProject;
  private final boolean myAsHtml;

  private final StringBuilder sb = new StringBuilder();

  public AnnotationTooltipBuilder(@NotNull Project project, boolean asHtml) {
    myProject = project;
    myAsHtml = asHtml;
  }

  private void append(@NotNull String text) {
    sb.append(myAsHtml ? XmlStringUtil.escapeString(text) : text);
  }

  private void appendRaw(@NotNull String text) {
    sb.append(text);
  }

  public void appendRevisionLine(@NotNull VcsRevisionNumber revisionNumber,
                                 @Nullable Convertor<? super VcsRevisionNumber, String> linkBuilder) {
    appendNewline();
    append("commit ");

    String link = myAsHtml && linkBuilder != null ? linkBuilder.convert(revisionNumber) : null;
    if (link != null) {
      appendRaw(link);
    }
    else {
      append(revisionNumber.asString());
    }
  }

  public void appendLine(@NotNull String content) {
    appendNewline();
    append(content);
  }

  public void appendCommitMessageBlock(@NotNull String message) {
    append("\n\n");
    appendTextWithLinks(message);
  }

  public void appendTextWithLinks(@NotNull String message) {
    if (myAsHtml) {
      appendRaw(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, message));
    }
    else {
      append(VcsUtil.trimCommitMessageToSaneSize(message));
    }
  }

  private void appendNewline() {
    if (sb.length() != 0) append("\n");
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  @NotNull
  public static String buildSimpleTooltip(@NotNull Project project,
                                          boolean asHtml,
                                          @NotNull String prefix,
                                          @NotNull String revision,
                                          @Nullable String commitMessage) {
    AnnotationTooltipBuilder builder = new AnnotationTooltipBuilder(project, asHtml);
    builder.append(prefix);
    builder.append(" ");
    builder.append(revision);
    if (commitMessage != null) {
      builder.append(": ");
      builder.appendTextWithLinks(commitMessage);
    }
    return builder.toString();
  }
}

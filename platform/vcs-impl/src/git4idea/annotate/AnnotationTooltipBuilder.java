// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotationTooltipBuilder {
  @NotNull private final Project myProject;
  private final boolean myAsHtml;

  private final @Nls StringBuilder sb = new StringBuilder();

  public AnnotationTooltipBuilder(@NotNull Project project, boolean asHtml) {
    myProject = project;
    myAsHtml = asHtml;
  }

  private void append(@NotNull @Nls String text) {
    sb.append(myAsHtml ? XmlStringUtil.escapeString(text) : text);
  }

  private void appendRaw(@NotNull @Nls String text) {
    sb.append(text);
  }

  public void appendRevisionLine(@NotNull VcsRevisionNumber revisionNumber,
                                 @Nullable Convertor<? super VcsRevisionNumber, String> linkBuilder) {
    appendNewline();

    String revision;
    if (myAsHtml) {
      revision = linkBuilder != null ? linkBuilder.convert(revisionNumber) : null;
      if (revision == null) {
        revision = XmlStringUtil.escapeString(revisionNumber.asString());
      }
    }
    else {
      revision = revisionNumber.asString();
    }
    appendRaw(VcsBundle.message("commit.description.tooltip.commit", revision));
  }

  public void appendLine(@NotNull @Nls String content) {
    appendNewline();
    append(content);
  }

  public void appendCommitMessageBlock(@NotNull @Nls String message) {
    append("\n\n");
    appendTextWithLinks(message);
  }

  public void appendTextWithLinks(@NotNull @Nls String message) {
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

  @Nls
  @Override
  public String toString() {
    return sb.toString();
  }

  @Nls
  @NotNull
  public static String buildSimpleTooltip(@NotNull Project project,
                                          boolean asHtml,
                                          @NotNull @Nls String prefix,
                                          @NotNull @NlsSafe String revision,
                                          @Nullable @Nls String commitMessage) {
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

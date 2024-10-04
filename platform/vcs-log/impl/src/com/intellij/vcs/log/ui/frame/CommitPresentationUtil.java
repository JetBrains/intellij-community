// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile;
import com.intellij.vcs.commit.message.SubjectLimitInspection;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.diff.comparison.TrimUtil.isPunctuation;
import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.openapi.vcs.ui.FontUtil.getCommitMessageFont;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

@ApiStatus.Internal
public final class CommitPresentationUtil {
  private static final @NotNull Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{7,40}", Pattern.CASE_INSENSITIVE);

  private static final @NotNull @NlsSafe String GO_TO_HASH = "go-to-hash:";
  private static final @NotNull @NlsSafe String SHOW_HIDE_BRANCHES = "show-hide-branches";
  private static final @NlsSafe String ELLIPSIS = "...";
  private static final int BIG_CUT_SIZE = 10;
  private static final double EPSILON = 1.5;

  public static boolean isShowHideBranches(@NotNull HyperlinkEvent e) {
    return SHOW_HIDE_BRANCHES.equals(e.getDescription());
  }

  public static boolean isGoToHash(@NotNull HyperlinkEvent e) {
    return e.getDescription().startsWith(GO_TO_HASH);
  }

  public static @NotNull String getAuthorPresentation(@NotNull VcsShortCommitDetails details) {
    String authorString = VcsUserUtil.getShortPresentation(details.getAuthor());
    return authorString + (VcsUserUtil.isSamePerson(details.getAuthor(), details.getCommitter()) ? "" : "*");
  }

  private static @NotNull @Nls String escapeMultipleSpaces(@NotNull @Nls String text) {
    @Nls StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == ' ') {
        if (i == text.length() - 1 || text.charAt(i + 1) != ' ') {
          result.append(' ');
        }
        else {
          result.append("&nbsp;");
        }
      }
      else if (text.charAt(i) == '\t') {
        result.append("&nbsp;&nbsp;&nbsp;&nbsp;");
      }
      else {
        result.append(text.charAt(i));
      }
    }
    return result.toString();
  }

  private static @NotNull Set<@NlsSafe String> findHashes(@NotNull @NlsSafe String text) {
    Set<String> result = new HashSet<>();
    Matcher matcher = HASH_PATTERN.matcher(text);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result;
  }

  private static @NotNull @NlsSafe String replaceHashes(@NotNull @NlsSafe String s, @NotNull Set<@NlsSafe String> resolvedHashes) {
    Matcher matcher = HASH_PATTERN.matcher(s);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      //noinspection HardCodedStringLiteral
      String hash = matcher.group();

      if (resolvedHashes.contains(hash)) {
        hash = HtmlChunk.link(GO_TO_HASH + hash, hash).toString();
      }
      matcher.appendReplacement(result, hash);
    }
    matcher.appendTail(result);

    return result.toString();
  }

  private static @NotNull Set<@NlsSafe String> findHashes(@NotNull Project project,
                                                          @NotNull @NlsSafe String message) {
    Set<String> unresolvedHashes = new HashSet<>();
    formatTextWithLinks(project, message, (@NlsSafe var s) -> {
      unresolvedHashes.addAll(findHashes(s));
      return s;
    });
    return unresolvedHashes;
  }

  private static @NotNull @NlsSafe String formatCommitText(@NotNull Project project,
                                                           @NotNull @NlsSafe String fullMessage,
                                                           @NotNull Set<@NlsSafe String> resolvedHashes) {
    fullMessage = VcsUtil.trimCommitMessageToSaneSize(fullMessage);

    Font font = getCommitMessageFont();
    Convertor<String, String> convertor = s -> replaceHashes(s, resolvedHashes);

    String subject = getSubject(fullMessage);
    String description = fullMessage.substring(subject.length());
    if (subject.contains("\n")) {
      // subject has new lines => that is not a subject
      return formatText(project, fullMessage, font, font.getStyle(), convertor);
    }

    if (isSubjectMarginEnabled(project)) {
      int margin = CommitMessageInspectionProfile.getSubjectRightMargin(project);
      if (margin < ELLIPSIS.length()) {
        margin = 5; //For cases when CommitMessageInspectionProfile wasn't loaded properly.
      }
      if (subject.length() > margin * EPSILON) {
        int placeToCut = margin - ELLIPSIS.length();
        for (int i = placeToCut; i >= Math.max(margin - BIG_CUT_SIZE, BIG_CUT_SIZE); i--) {
          if (subject.charAt(i) == ' ') {
            placeToCut = i;
            break;
          }
        }

        String tail = subject.substring(placeToCut);
        subject = subject.substring(0, placeToCut);
        if (isPunctuation(subject.charAt(placeToCut - 1))) {
          tail = StringUtil.trimStart(tail, " ");
        }
        else {
          subject += ELLIPSIS;
          tail = ELLIPSIS + tail;
        }
        description = "\n\n" + tail + description;
      }
    }

    return "<b>" +
           formatText(project, subject, font, Font.BOLD, convertor) +
           "</b>" +
           formatText(project, description, font, font.getStyle(), convertor);
  }

  public static @NotNull @NlsSafe String getSubject(@NotNull @NlsSafe String fullMessage) {
    int separator = fullMessage.indexOf("\n\n");
    return separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
  }

  public static boolean isSubjectMarginEnabled(@NotNull Project project) {
    return CommitMessageInspectionProfile.getInstance(project).isToolEnabled(SubjectLimitInspection.class);
  }

  private static @NotNull String formatText(@NotNull Project project,
                                            @NotNull @Nls String text,
                                            @NotNull Font font,
                                            int style,
                                            @NotNull Convertor<? super String, String> convertor) {
    return FontUtil.getHtmlWithFonts(escapeMultipleSpaces(formatTextWithLinks(project, text, convertor)), style, font);
  }

  private static @NotNull @Nls String getAuthorAndCommitterText(@NotNull VcsUser author, long authorTime,
                                                                @NotNull VcsUser committer, long commitTime) {

    String authorText = VcsLogBundle.message("vcs.log.details.author.on.date.at.time",
                                             getAuthorName(author),
                                             DateFormatUtil.formatDate(authorTime),
                                             DateFormatUtil.formatTime(authorTime));

    HtmlBuilder builder = new HtmlBuilder().appendRaw(authorText);
    if (!VcsUserUtil.isSamePerson(author, committer)) {
      builder.br().append(getCommitterHtml(committer, commitTime != authorTime ? commitTime : null));
    }
    else if (authorTime != commitTime) {
      builder.br().append(getCommitterHtml(null, commitTime));
    }

    return builder.toString();
  }

  private static @NotNull HtmlChunk getCommitterHtml(@Nullable VcsUser committer, @Nullable Long commitTime) {
    if (committer == null && commitTime == null) {
      return HtmlChunk.empty();
    }

    String style = "color:#" + ColorUtil.toHex(JBColor.GRAY);
    HtmlChunk.Element graySpan = HtmlChunk.span(style);

    String committed;
    if (committer == null) {
      String date = DateFormatUtil.formatDate(commitTime);
      String time = DateFormatUtil.formatTime(commitTime);
      committed = VcsLogBundle.message("vcs.log.details.committer.info.date.time", date, time);
    }
    else {
      String by = VcsUserUtil.getShortPresentation(committer) +
                  (!committer.getEmail().isEmpty() ? "</span> " + getEmailLink(committer) + "<span style=\"" + style + "\">" : "");
      if (commitTime == null) {
        committed = VcsLogBundle.message("vcs.log.details.committer.info.user", by);
      }
      else {
        String date = DateFormatUtil.formatDate(commitTime);
        String time = DateFormatUtil.formatTime(commitTime);
        committed = VcsLogBundle.message("vcs.log.details.committer.info.user.date.time", by, date, time);
      }
    }
    return new HtmlBuilder().appendRaw(committed).wrapWith(graySpan);
  }

  private static @NotNull @NlsSafe String getAuthorName(@NotNull VcsUser user) {
    String username = VcsUserUtil.getShortPresentation(user);
    return user.getEmail().isEmpty() ? username : username + " " + getEmailLink(user);
  }

  private static @NotNull HtmlChunk.Element getEmailLink(@NotNull VcsUser user) {
    return HtmlChunk.link("mailto:" + user.getEmail(), "<" + user.getEmail() + ">");
  }

  private static @NotNull @Nls String formatCommitHashAndAuthor(@NotNull VcsCommitMetadata commit) {
    return formatCommitHashAndAuthor(commit.getId(),
                                     commit.getAuthor(), commit.getAuthorTime(),
                                     commit.getCommitter(), commit.getCommitTime());
  }

  public static @NotNull @Nls String formatCommitHashAndAuthor(@NotNull Hash commitId,
                                                               @NotNull VcsUser author, long authorTime,
                                                               @NotNull VcsUser committer, long commitTime) {
    Font font = FontUtil.getCommitMetadataFont();
    return FontUtil.getHtmlWithFonts(commitId.toShortString() + " " + getAuthorAndCommitterText(author, authorTime, committer, commitTime),
                                     font.getStyle(), font);
  }

  public static @NotNull @Nls String getBranchesLinkText(@Nullable List<@NlsSafe String> branches,
                                                         boolean expanded,
                                                         int availableWidth,
                                                         @NotNull FontMetrics metrics) {
    if (branches == null) {
      return VcsLogBundle.message("vcs.log.details.in.branches.loading");
    }
    if (branches.isEmpty()) return VcsLogBundle.message("vcs.log.details.in.branches.empty");

    String head = VcsLogBundle.message("vcs.log.details.in.branches", branches.size()) + " ";

    if (expanded) {
      return new HtmlBuilder().append(head)
        .append(HtmlChunk.link(SHOW_HIDE_BRANCHES, VcsLogBundle.message("vcs.log.details.in.branches.hide")))
        .toString();
    }

    String tail = "… " + HtmlChunk.link(SHOW_HIDE_BRANCHES, VcsLogBundle.message("vcs.log.details.in.branches.show.all"));
    int headAndTailWidth = metrics.stringWidth(head) + metrics.stringWidth(StringUtil.removeHtmlTags(tail));
    if (availableWidth <= headAndTailWidth) {
      return head + tail;
    }
    availableWidth -= headAndTailWidth;

    StringBuilder branchesText = new StringBuilder();
    for (int i = 0; i < branches.size(); i++) {
      String branch = branches.get(i) + (i != branches.size() - 1 ? ", " : "");
      int branchWidth = metrics.stringWidth(branch);
      if (branchWidth < availableWidth) {
        branchesText.append(branch);
        availableWidth -= branchWidth;
      }
      else {
        StringBuilder shortenedBranch = new StringBuilder();
        for (char c : branch.toCharArray()) {
          if (metrics.stringWidth(shortenedBranch.toString() + c) >= availableWidth) {
            break;
          }
          shortenedBranch.append(c);
        }
        branchesText.append(shortenedBranch);
        branchesText.append(tail);
        break;
      }
    }

    return head + branchesText;
  }

  public static @NotNull CommitPresentation buildPresentation(@NotNull Project project,
                                                              @NotNull VcsCommitMetadata commit,
                                                              @NotNull Set<? super String> unresolvedHashes) {
    String rawMessage = commit.getFullMessage();
    String hashAndAuthor = formatCommitHashAndAuthor(commit);

    Set<String> unresolvedHashesForCommit = findHashes(project, rawMessage);
    if (unresolvedHashesForCommit.isEmpty()) {
      return new CommitPresentation(project, commit.getRoot(), rawMessage, hashAndAuthor, MultiMap.empty());
    }

    unresolvedHashes.addAll(unresolvedHashesForCommit);
    return new UnresolvedPresentation(project, commit.getRoot(), rawMessage, hashAndAuthor);
  }

  private static class UnresolvedPresentation extends CommitPresentation {
    UnresolvedPresentation(@NotNull Project project,
                           @NotNull VirtualFile root,
                           @NotNull @NlsSafe String rawMessage,
                           @NotNull @Nls String hashAndAuthor) {
      super(project, root, rawMessage, hashAndAuthor, MultiMap.empty());
    }

    @Override
    public @NotNull CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      return new CommitPresentation(myProject, myRoot, myRawMessage, myHashAndAuthor, resolvedHashes);
    }

    @Override
    public boolean isResolved() {
      return false;
    }
  }

  public static class CommitPresentation {
    protected final @NotNull Project myProject;
    protected final @NotNull @NlsSafe String myRawMessage;
    protected final @NotNull @Nls String myHashAndAuthor;
    protected final @NotNull VirtualFile myRoot;
    private final @NotNull MultiMap<@NlsSafe String, CommitId> myResolvedHashes;

    public CommitPresentation(@NotNull Project project,
                              @NotNull VirtualFile root,
                              @NotNull @NlsSafe String rawMessage,
                              @NotNull @Nls String hashAndAuthor,
                              @NotNull MultiMap<@NlsSafe String, CommitId> resolvedHashes) {
      myProject = project;
      myRoot = root;
      myRawMessage = rawMessage;
      myHashAndAuthor = hashAndAuthor;
      myResolvedHashes = resolvedHashes;
    }

    public @NotNull @NlsSafe String getText() {
      return formatCommitText(myProject, myRawMessage, myResolvedHashes.keySet());
    }

    public @NotNull @Nls String getHashAndAuthor() {
      return myHashAndAuthor;
    }

    public @Nullable CommitId parseTargetCommit(@NotNull HyperlinkEvent e) {
      if (!e.getDescription().startsWith(GO_TO_HASH)) return null;
      String hash = e.getDescription().substring(GO_TO_HASH.length());
      Collection<CommitId> ids = myResolvedHashes.get(hash);
      if (ids.size() <= 1) return getFirstItem(ids);
      for (CommitId id : ids) {
        if (myRoot.equals(id.getRoot())) {
          return id;
        }
      }
      return getFirstItem(ids);
    }

    public @NotNull CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      return this;
    }

    public boolean isResolved() {
      return true;
    }
  }
}

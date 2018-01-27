// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.openapi.vcs.ui.FontUtil.getCommitMessageFont;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class CommitPresentationUtil {
  @NotNull private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{7,40}", Pattern.CASE_INSENSITIVE);
  private static final int PER_ROW = 2;

  @NotNull static final String GO_TO_HASH = "go-to-hash:";
  @NotNull static final String SHOW_HIDE_BRANCHES = "show-hide-branches";

  @NotNull
  private static String escapeMultipleSpaces(@NotNull String text) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == ' ') {
        if (i == text.length() - 1 || text.charAt(i + 1) != ' ') {
          result.append(' ');
        }
        else {
          result.append("&nbsp;");
        }
      }
      else {
        result.append(text.charAt(i));
      }
    }
    return result.toString();
  }

  @NotNull
  private static Set<String> findHashes(@NotNull String text) {
    Set<String> result = ContainerUtil.newHashSet();
    Matcher matcher = HASH_PATTERN.matcher(text);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result;
  }

  @NotNull
  private static String replaceHashes(@NotNull String s, @NotNull Set<String> resolvedHashes) {
    Matcher matcher = HASH_PATTERN.matcher(s);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String hash = matcher.group();

      if (resolvedHashes.contains(hash)) {
        hash = "<a href=\"" + GO_TO_HASH + hash + "\">" + hash + "</a>";
      }
      matcher.appendReplacement(result, hash);
    }
    matcher.appendTail(result);

    return result.toString();
  }

  @NotNull
  private static Set<String> findHashes(@NotNull Project project,
                                        @NotNull String subject,
                                        @NotNull String description) {
    Set<String> unresolvedHashes = ContainerUtil.newHashSet();
    Convertor<String, String> convertor = s -> {
      unresolvedHashes.addAll(findHashes(s));
      return s;
    };
    formatTextWithLinks(project, subject, convertor);
    formatTextWithLinks(project, description, convertor);
    return unresolvedHashes;
  }

  @NotNull
  private static String formatCommitText(@NotNull Project project,
                                         @NotNull String subject,
                                         @NotNull String description,
                                         @NotNull Set<String> resolvedHashes) {
    Convertor<String, String> convertor = s -> replaceHashes(s, resolvedHashes);
    Font font = getCommitMessageFont();
    return "<b>" +
           FontUtil.getHtmlWithFonts(escapeMultipleSpaces(formatTextWithLinks(project, subject, convertor)), Font.BOLD, font) +
           "</b>" +
           FontUtil.getHtmlWithFonts(escapeMultipleSpaces(formatTextWithLinks(project, description, convertor)), font.getStyle(), font);
  }

  @NotNull
  private static String getAuthorText(@NotNull VcsFullCommitDetails commit, int offset) {
    long authorTime = commit.getAuthorTime();
    long commitTime = commit.getCommitTime();

    String authorText = getAuthorName(commit.getAuthor()) + formatDateTime(authorTime);
    if (!VcsUserUtil.isSamePerson(commit.getAuthor(), commit.getCommitter())) {
      String commitTimeText;
      if (authorTime != commitTime) {
        commitTimeText = formatDateTime(commitTime);
      }
      else {
        commitTimeText = "";
      }
      authorText += getCommitterText(commit.getCommitter(), commitTimeText, offset);
    }
    else if (authorTime != commitTime) {
      authorText += getCommitterText(null, formatDateTime(commitTime), offset);
    }
    return authorText;
  }

  @NotNull
  private static String getCommitterText(@Nullable VcsUser committer, @NotNull String commitTimeText, int offset) {
    String alignment = "<br/>" + StringUtil.repeat("&nbsp;", offset);
    String gray = ColorUtil.toHex(JBColor.GRAY);

    String graySpan = "<span style='color:#" + gray + "'>";

    String text = alignment + graySpan + "committed";
    if (committer != null) {
      text += " by " + VcsUserUtil.getShortPresentation(committer);
      if (!committer.getEmail().isEmpty()) {
        text += "</span>" + getEmailText(committer) + graySpan;
      }
    }
    text += commitTimeText + "</span>";
    return text;
  }

  @NotNull
  private static String getAuthorName(@NotNull VcsUser user) {
    String username = VcsUserUtil.getShortPresentation(user);
    return user.getEmail().isEmpty() ? username : username + getEmailText(user);
  }

  @NotNull
  private static String getEmailText(@NotNull VcsUser user) {
    return " <a href='mailto:" + user.getEmail() + "'>&lt;" + user.getEmail() + "&gt;</a>";
  }

  @NotNull
  public static String formatDateTime(long time) {
    return " on " + DateFormatUtil.formatDate(time) + " at " + DateFormatUtil.formatTime(time);
  }

  @NotNull
  static String getBranchesText(@Nullable List<String> branches, boolean expanded) {
    if (branches == null) {
      return "<i>In branches: loading...</i>";
    }
    if (branches.isEmpty()) return "<i>Not in any branch</i>";

    if (expanded) {
      int rowCount = (int)Math.ceil((double)branches.size() / PER_ROW);

      int[] means = new int[PER_ROW - 1];
      int[] max = new int[PER_ROW - 1];

      for (int i = 0; i < rowCount; i++) {
        for (int j = 0; j < PER_ROW - 1; j++) {
          int index = rowCount * j + i;
          if (index < branches.size()) {
            means[j] += branches.get(index).length();
            max[j] = Math.max(branches.get(index).length(), max[j]);
          }
        }
      }
      for (int j = 0; j < PER_ROW - 1; j++) {
        means[j] /= rowCount;
      }

      HtmlTableBuilder builder = new HtmlTableBuilder();
      for (int i = 0; i < rowCount; i++) {
        builder.startRow();
        for (int j = 0; j < PER_ROW; j++) {
          int index = rowCount * j + i;
          if (index >= branches.size()) {
            builder.append("");
          }
          else {
            String branch = branches.get(index);
            if (index != branches.size() - 1) {
              int space = 0;
              if (j < PER_ROW - 1 && branch.length() == max[j]) {
                space = Math.max(means[j] + 20 - max[j], 5);
              }
              builder.append(branch + StringUtil.repeat("&nbsp;", space), "left");
            }
            else {
              builder.append(branch, "left");
            }
          }
        }

        builder.endRow();
      }

      return "<i>In " + branches.size() + " branches:</i> " +
             "<a href=\"" + SHOW_HIDE_BRANCHES + "\"><i>(click to hide)</i></a><br>" +
             builder.build();
    }
    else {
      int totalMax = 0;
      int charCount = 0;
      for (String b : branches) {
        totalMax++;
        charCount += b.length();
        if (charCount >= 50) break;
      }

      String branchText;
      if (branches.size() <= totalMax) {
        branchText = StringUtil.join(branches, ", ");
      }
      else {
        branchText = StringUtil.join(ContainerUtil.getFirstItems(branches, totalMax), ", ") +
                     "… <a href=\"" +
                     SHOW_HIDE_BRANCHES +
                     "\"><i>(click to show all)</i></a>";
      }
      return "<i>In " + branches.size() + StringUtil.pluralize(" branch", branches.size()) + ":</i> " + branchText;
    }
  }

  @NotNull
  public static CommitPresentation buildPresentation(@NotNull Project project,
                                                     @NotNull VcsFullCommitDetails commit,
                                                     @NotNull Set<String> unresolvedHashes) {
    Font font = FontUtil.getCommitMetadataFont();
    String hash = commit.getId().toShortString();
    String hashAndAuthor = FontUtil.getHtmlWithFonts(hash + " " + getAuthorText(commit, hash.length() + 1), font.getStyle(), font);
    String fullMessage = commit.getFullMessage();
    int separator = fullMessage.indexOf("\n\n");
    String subject = separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
    String description = fullMessage.substring(subject.length());

    Set<String> unresolvedHashesForCommit = findHashes(project, subject, description);
    String text = formatCommitText(project, subject, description, Collections.emptySet());
    if (unresolvedHashesForCommit.isEmpty()) {
      return new CommitPresentation(text, hashAndAuthor, commit.getRoot(), MultiMap.empty());
    }

    unresolvedHashes.addAll(unresolvedHashesForCommit);
    return new UnresolvedPresentation(project, commit.getRoot(), subject, description, hashAndAuthor, text);
  }

  private static class UnresolvedPresentation extends CommitPresentation {
    private final Project myProject;
    private final String mySubject;
    private final String myDescription;

    public UnresolvedPresentation(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  @NotNull String subject,
                                  @NotNull String description,
                                  @NotNull String hashAndAuthor,
                                  @NotNull String text) {
      super(text, hashAndAuthor, root, MultiMap.empty());
      myProject = project;
      mySubject = subject;
      myDescription = description;
    }

    @NotNull
    public CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      String text = formatCommitText(myProject, mySubject, myDescription, resolvedHashes.keySet());
      return new CommitPresentation(text, myHashAndAuthor, myRoot, resolvedHashes);
    }

    @Override
    public boolean isResolved() {
      return false;
    }
  }

  public static class CommitPresentation {
    @NotNull protected final String myText;
    @NotNull protected final String myHashAndAuthor;
    @NotNull protected final VirtualFile myRoot;
    @NotNull private final MultiMap<String, CommitId> myResolvedHashes;

    public CommitPresentation(@NotNull String text, @NotNull String hashAndAuthor,
                              @NotNull VirtualFile root, @NotNull MultiMap<String, CommitId> resolvedHashes) {
      myText = text;
      myHashAndAuthor = hashAndAuthor;
      myRoot = root;
      myResolvedHashes = resolvedHashes;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public String getHashAndAuthor() {
      return myHashAndAuthor;
    }

    @Nullable
    public CommitId parseTargetCommit(@NotNull HyperlinkEvent e) {
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

    @NotNull
    public CommitPresentation resolve(@NotNull MultiMap<String, CommitId> resolvedHashes) {
      return this;
    }

    public boolean isResolved() {
      return true;
    }
  }
}

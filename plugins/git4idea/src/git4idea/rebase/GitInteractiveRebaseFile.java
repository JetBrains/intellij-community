// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

class GitInteractiveRebaseFile {
  private final @NotNull Project myProject;
  private final @NotNull VirtualFile myRoot;
  private final @NotNull File myFile;

  GitInteractiveRebaseFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull File file) {
    myProject = project;
    myRoot = root;
    myFile = file;
  }

  public @NotNull List<GitRebaseEntry> load() throws IOException, NoopException, VcsException {
    String commentChar = GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(myProject) ?
                         GitUtil.COMMENT_CHAR : "#";

    Charset encoding = GitConfigUtil.getLogEncodingCharset(myProject, myRoot);
    List<GitRebaseEntry> entries = new ArrayList<>();
    final StringScanner s = new StringScanner(FileUtil.loadFile(myFile, encoding));
    boolean noop = false;
    while (s.hasMoreData()) {
      if (s.isEol() || isComment(s, commentChar)) {
        s.nextLine();
        continue;
      }
      if (s.startsWith("noop")) {
        noop = true;
        s.nextLine();
        continue;
      }
      String command = s.spaceToken();
      final GitRebaseEntry.Action action = GitRebaseEntry.parseAction(command);

      String hash = s.spaceToken();
      while (true) {
        boolean paramConsumed = action.consumeParameter(hash);
        if (!paramConsumed) break;
        hash = s.spaceToken();
      }

      String comment = s.line();
      String subject = trimCommentIfNeeded(comment, commentChar);

      entries.add(new GitRebaseEntry(action, hash, subject));
    }
    if (noop && entries.isEmpty()) {
      throw new NoopException();
    }
    return entries;
  }

  private static boolean isComment(@NotNull StringScanner s, @NotNull String commentChar) {
    return s.startsWith(commentChar);
  }

  private static String trimCommentIfNeeded(@NotNull String line, @NotNull String commentChar) {
    // Ex: 'f efefef subject # empty' for commits created with '--allow-empty'
    int i = line.indexOf(commentChar);
    if (i == -1) return line;
    return line.substring(0, i).trim();
  }

  public void cancel() throws IOException {
    try (PrintWriter out = new PrintWriter(new FileWriter(myFile))) {
      out.println("# rebase is cancelled");
    }
  }

  public void save(@NotNull List<? extends GitRebaseEntry> entries) throws IOException {
    Charset encoding = GitConfigUtil.getLogEncodingCharset(myProject, myRoot);
    try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(myFile), encoding))) {
      boolean knowsDropAction = GitVersionSpecialty.KNOWS_REBASE_DROP_ACTION.existsIn(myProject);
      for (GitRebaseEntry e : entries) {
        if (e.getAction() != GitRebaseEntry.Action.DROP.INSTANCE || knowsDropAction) {
          out.println(e);
        }
      }
    }
  }

  static class NoopException extends Exception {
  }
}

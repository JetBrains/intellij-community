// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

class GitInteractiveRebaseFile {
  @NonNls private static final String CYGDRIVE_PREFIX = "/cygdrive/";

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final String myFile;

  GitInteractiveRebaseFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull String rebaseFilePath) {
    myProject = project;
    myRoot = root;
    myFile = adjustFilePath(rebaseFilePath);
  }

  @NotNull
  public List<GitRebaseEntry> load() throws IOException, NoopException {
    String encoding = GitConfigUtil.getLogEncoding(myProject, myRoot);
    List<GitRebaseEntry> entries = new ArrayList<>();
    final StringScanner s = new StringScanner(FileUtil.loadFile(new File(myFile), encoding));
    boolean noop = false;
    while (s.hasMoreData()) {
      if (s.isEol() || isComment(s)) {
        s.nextLine();
        continue;
      }
      if (s.startsWith("noop")) {
        noop = true;
        s.nextLine();
        continue;
      }
      String action = s.spaceToken();
      String hash = s.spaceToken();
      String comment = s.line();

      entries.add(new GitRebaseEntry(action, hash, comment));
    }
    if (noop && entries.isEmpty()) {
      throw new NoopException();
    }
    return entries;
  }

  private boolean isComment(@NotNull StringScanner s) {
    String commentChar = GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(myProject) ?
                         GitUtil.COMMENT_CHAR : "#";
    return s.startsWith(commentChar);
  }

  public void cancel() throws IOException {
    try (PrintWriter out = new PrintWriter(new FileWriter(myFile))) {
      out.println("# rebase is cancelled");
    }
  }

  public void save(@NotNull List<? extends GitRebaseEntry> entries) throws IOException {
    String encoding = GitConfigUtil.getLogEncoding(myProject, myRoot);
    try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(myFile), encoding))) {
      for (GitRebaseEntry e : entries) {
        if (e.getAction() != GitRebaseEntry.Action.SKIP) {
          out.println(e.getAction().toString() + " " + e.getCommit() + " " + e.getSubject());
        }
      }
    }
  }

  @NotNull
  private static String adjustFilePath(@NotNull String file) {
    if (SystemInfo.isWindows && file.startsWith(CYGDRIVE_PREFIX)) {
      final int prefixSize = CYGDRIVE_PREFIX.length();
      return file.substring(prefixSize, prefixSize + 1) + ":" + file.substring(prefixSize + 1);
    }
    return file;
  }

  static class NoopException extends Exception {
  }
}

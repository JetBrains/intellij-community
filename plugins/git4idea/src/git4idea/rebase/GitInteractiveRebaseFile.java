/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.config.GitConfigUtil;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
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
    List<GitRebaseEntry> entries = ContainerUtil.newArrayList();
    final StringScanner s = new StringScanner(FileUtil.loadFile(new File(myFile), encoding));
    boolean noop = false;
    while (s.hasMoreData()) {
      if (s.isEol() || s.startsWith(GitUtil.COMMENT_CHAR)) {
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

  public void cancel() throws IOException {
    PrintWriter out = new PrintWriter(new FileWriter(myFile));
    try {
      out.println("# rebase is cancelled");
    }
    finally {
      out.close();
    }
  }

  public void save(@NotNull List<GitRebaseEntry> entries) throws IOException {
    String encoding = GitConfigUtil.getLogEncoding(myProject, myRoot);
    PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(myFile), encoding));
    try {
      for (GitRebaseEntry e : entries) {
        if (e.getAction() != GitRebaseEntry.Action.skip) {
          out.println(e.getAction().toString() + " " + e.getCommit() + " " + e.getSubject());
        }
      }
    }
    finally {
      out.close();
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

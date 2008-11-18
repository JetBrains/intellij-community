/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.config.GitConfigUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * A git remotes
 */
public final class GitRemote {
  /**
   * The name of the remote
   */
  private final String myName;
  /**
   * The url of the remote
   */
  private final String myUrl;
  /**
   * Prefix for url in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_URL_PREFIX = "  URL: ";
  /**
   * Prefix for local branch maping in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_MAPPING_PREFIX = "  Remote branch merged with 'git pull' while on branch ";
  /**
   * line that starts branches section in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_BRANCHES_LINE = "  Tracked remote branches";
  /**
   * US-ASCII encoding name
   */
  @NonNls private static final String US_ASCII_ENCODING = "US-ASCII";

  /**
   * A constructor
   *
   * @param name the name
   * @param url  the url
   */
  public GitRemote(@NotNull final String name, final String url) {
    myName = name;
    myUrl = url;
  }

  /**
   * @return the name of the remote
   */
  public String name() {
    return myName;
  }

  /**
   * @return the url of the remote
   */
  public String url() {
    return myUrl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object obj) {
    return (obj instanceof GitRemote) && myName.equals(((GitRemote)obj).myName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return myName;
  }

  /**
   * List all remotes for the git root (git remote -v)
   *
   * @param project the context project
   * @param root    the git root
   * @return a list of registered remotes
   */
  public static List<GitRemote> list(Project project, VirtualFile root) throws VcsException {
    ArrayList<GitRemote> remotes = new ArrayList<GitRemote>();
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.REMOTE);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("-v");
    for (String line : handler.run().split("\n")) {
      int i = line.indexOf('\t');
      if (i == -1) {
        continue;
      }
      String name = line.substring(0, i);
      String url = line.substring(i + 1);
      remotes.add(new GitRemote(name, url));
    }
    return remotes;
  }

  /**
   * Get information about remote stored in localy (remote end is not queried about branches)
   *
   * @return a information about remotes
   */
  public Info localInfo(Project project, VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.REMOTE);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("show", "-n", myName);
    String[] lines = handler.run().split("\n");
    TreeMap<String, String> mapping = new TreeMap<String, String>();
    TreeSet<String> branches = new TreeSet<String>();
    int i = 0;
    if (!lines[i].startsWith("*") || !lines[i].endsWith(myName)) {
      throw new IllegalStateException("Unexpected format for 'git remote show' line " + i + ":" + lines[i]);
    }
    if (i >= lines.length) {
      throw new IllegalStateException("Premature end from 'git remote show' at line " + i);
    }
    i++;
    if (!lines[i].startsWith(SHOW_URL_PREFIX) || !lines[i].endsWith(myUrl)) {
      throw new IllegalStateException("Unexpected format for 'git remote show' line " + i + ":" + lines[i]);
    }
    i++;
    while (i < lines.length && lines[i].startsWith(SHOW_MAPPING_PREFIX)) {
      String local = lines[i].substring(SHOW_MAPPING_PREFIX.length());
      i++;
      String remote = lines[i].trim();
      i++;
      mapping.put(local, remote);
    }
    if (i < lines.length && lines[i].equals(SHOW_BRANCHES_LINE)) {
      i++;
      branches.addAll(Arrays.asList(lines[i].substring(4).split(" ")));
    }
    return new Info(Collections.unmodifiableSortedMap(mapping), Collections.unmodifiableSortedSet(branches));
  }

  /**
   * Get list of fetch specifications for the configured remote
   *
   * @param project    the project name
   * @param root       the git root
   * @param remoteName the name of the remote
   * @return the configured fetch specifications for remote
   */
  public static List<String> getFetchSpecs(Project project, VirtualFile root, String remoteName) throws VcsException {
    ArrayList<String> rc = new ArrayList<String>();
    final File rootFile = VfsUtil.virtualToIoFile(root);
    @NonNls final File remotesFile = new File(rootFile, ".git" + File.separator + "remotes" + File.separator + remoteName);
    // TODO try branches file?
    if (remotesFile.exists() && !remotesFile.isDirectory()) {
      // try remotes file
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        String text = FileUtil.loadTextAndClose(new InputStreamReader(new FileInputStream(remotesFile), US_ASCII_ENCODING));
        @NonNls String pullPrefix = "Pull:";
        for (StringScanner s = new StringScanner(text); s.hasMoreData();) {
          String line = s.line();
          if (line.startsWith(pullPrefix)) {
            rc.add(line.substring(pullPrefix.length()).trim());
          }
        }
      }
      catch (IOException e) {
        throw new VcsException("Unable to read remotes file: " + remotesFile, e);
      }
    }
    else {
      // try config file
      for (Pair<String, String> pair : GitConfigUtil.getAllValues(project, root, "remote." + remoteName + ".fetch")) {
        rc.add(pair.second);
      }
    }
    return rc;
  }

  /**
   * Information about git remote
   */
  public class Info {
    /**
     * Branch mappings
     */
    private final Map<String, String> myBranchMapping;
    /**
     * Tracked remote branches
     */
    private final Set<String> myTrackedRemotes;

    /**
     * A constructor from fields
     *
     * @param branchMapping  a map from local branches to remote branches
     * @param trackedRemotes a set of tracked remotes
     */
    public Info(final Map<String, String> branchMapping, final Set<String> trackedRemotes) {
      myBranchMapping = branchMapping;
      myTrackedRemotes = trackedRemotes;
    }

    /**
     * @return a remote for this information object
     */
    public GitRemote remote() {
      return GitRemote.this;
    }

    /**
     * Get remote branch for the local branch
     *
     * @param localBranchName a local branch name
     * @return a remote branch name or null if the mapping is not found
     */
    @Nullable
    public String getRemoteForLocal(final String localBranchName) {
      return myBranchMapping.get(localBranchName);
    }

    /**
     * A set of tracked remotes
     *
     * @return a set of tracked remotes
     */
    public Set<String> trackedBranches() {
      return myTrackedRemotes;
    }
  }
}

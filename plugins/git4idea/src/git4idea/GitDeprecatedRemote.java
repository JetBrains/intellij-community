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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @deprecated Use {@link git4idea.repo.GitRemote} to work with remotes. Use {@link GitRepository#getConfig#getRemotes()} to get the list
 * of remotes.
 * 
 * A git remotes
 */
public final class GitDeprecatedRemote {
  /**
   * The name of the remote
   */
  private final String myName;
  /**
   * The fetch url of the remote
   */
  private final String myFetchUrl;
  /**
   * The push url of the remote
   */
  private final String myPushUrl;
  /**
   * Prefix for url in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_URL_PREFIX = "  URL: ";
  /**
   * Prefix for url in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_FETCH_URL_PREFIX = "  Fetch URL: ";
  /**
   * Prefix for url in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_PUSH_URL_PREFIX = "  Push  URL: ";
  /**
   * Prefix for local branch mapping in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_MAPPING_PREFIX = "  Remote branch merged with 'git pull' while on branch ";
  /**
   * line that starts branches section in "git remote show -n {branch}"
   */
  @NonNls private static final String SHOW_BRANCHES_LINE = "  Tracked remote branch";
  /**
   * Pattern that parses pull spec
   */
  private static final Pattern PULL_PATTERN = Pattern.compile("(\\S+)\\s+merges with remote (\\S+)");

  /**
   * A constructor
   *
   * @param name the name
   * @param url  the url
   */
  public GitDeprecatedRemote(@NotNull final String name, final String url) {
    this(name, url, url);
  }

  /**
   * A constructor
   *
   * @param name     the name
   * @param fetchUrl the fetch url
   * @param pushUrl  the fetch url
   */
  public GitDeprecatedRemote(String name, String fetchUrl, String pushUrl) {
    myName = name;
    myFetchUrl = fetchUrl;
    myPushUrl = pushUrl;
  }

  /**
   * @return the name of the remote
   */
  public String name() {
    return myName;
  }

  /**
   * @return the fetch url of the remote
   */
  public String fetchUrl() {
    return myFetchUrl;
  }

  /**
   * @return the push url of the remote
   */
  public String pushUrl() {
    return myPushUrl;
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
    return (obj instanceof GitDeprecatedRemote) && myName.equals(((GitDeprecatedRemote)obj).myName);
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
   * @throws VcsException in case of git error
   */
  public static List<GitDeprecatedRemote> list(Project project, VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("-v");
    String output = handler.run();
    return parseRemoteListInternal(output);
  }

  /**
   * Parse list of remotes (internal method)
   *
   * @param output the output to parse
   * @return list of remotes
   */
  public static List<GitDeprecatedRemote> parseRemoteListInternal(String output) {
    ArrayList<GitDeprecatedRemote> remotes = new ArrayList<GitDeprecatedRemote>();
    StringScanner s = new StringScanner(output);
    String name = null;
    String fetch = null;
    String push = null;
    while (s.hasMoreData()) {
      String n = s.tabToken();
      if (name != null && !n.equals(name) && fetch != null) {
        if (push == null) {
          push = fetch;
        }
        remotes.add(new GitDeprecatedRemote(name, fetch, push));
        fetch = null;
        push = null;
      }
      name = n;
      String url = s.line();
      if (url.endsWith(" (push)")) {
        push = url.substring(0, url.length() - " (push)".length());
      }
      else if (url.endsWith(" (fetch)")) {
        fetch = url.substring(0, url.length() - " (fetch)".length());
      }
      else {
        fetch = url;
        push = url;
      }
    }
    if (name != null && fetch != null) {
      if (push == null) {
        push = fetch;
      }
      remotes.add(new GitDeprecatedRemote(name, fetch, push));
    }
    return remotes;
  }

  /**
   * Parse output of the remote (internal method)
   *
   * @param name   the name of the remote
   * @param output the output of "git remote show -n {name}" command
   * @return the parsed remote
   */
  public static GitDeprecatedRemote parseRemoteInternal(String name, String output) {
    StringScanner in = new StringScanner(output);
    if (!in.tryConsume("* ")) {
      throw new IllegalStateException(unexpectedFormat(name, output));
    }
    String nameLine = in.line();
    if (!nameLine.endsWith(name)) {
      throw new IllegalStateException("Name line of 'git remote show' ends with wrong name: " + nameLine);
    }
    String fetch = null;
    String push = null;
    if (in.tryConsume(SHOW_URL_PREFIX)) {
      fetch = in.line();
      push = fetch;
    }
    else if (in.tryConsume(SHOW_FETCH_URL_PREFIX)) {
      fetch = in.line();
      if (in.tryConsume(SHOW_PUSH_URL_PREFIX)) {
        push = in.line();
      }
      else {
        push = fetch;
      }
    }
    else {
      throw new IllegalStateException(unexpectedFormat(name, output));
    }
    return new GitDeprecatedRemote(name, fetch, push);
  }

  private static String unexpectedFormat(String remoteName, String output) {
    return String.format("Unexpected format for 'git remote show'. Remote: %s, output:%n%s", remoteName, output);
  }


  /**
   * Parse remote information
   *
   * @param output the output of "git remote show -n {name}" command
   * @return the parsed remote
   */
  public Info parseInfoInternal(String output) {
    TreeMap<String, String> mapping = new TreeMap<String, String>();
    TreeSet<String> branches = new TreeSet<String>();
    StringScanner s = new StringScanner(output);
    if (s.tryConsume("* ") && !s.line().endsWith(myName)) {
      throw new IllegalStateException("Unexpected format for 'git remote show'" + output);
    }
    if (!s.hasMoreData()) {
      throw new IllegalStateException("Premature end from 'git remote show'" + output);
    }
    do {
      if (s.tryConsume(SHOW_MAPPING_PREFIX)) {
        // old format
        String local = s.line();
        String remote = s.line().trim();
        mapping.put(local, remote);
      }
      else if (s.tryConsume(SHOW_BRANCHES_LINE)) {
        s.line();
        if (s.tryConsume("    ")) {
          ContainerUtil.addAll(branches, s.line().split(" "));
        }
      }
      else if (s.tryConsume("  Remote branch")) {
        s.line();
        while (s.tryConsume("    ")) {
          branches.add(s.line().trim());
        }
      }
      else if (s.tryConsume("  Local branch configured for 'git pull':")) {
        s.line();
        while (s.tryConsume("    ")) {
          Matcher m = PULL_PATTERN.matcher(s.line());
          if (m.matches()) {
            String local = m.group(1);
            String remote = m.group(2);
            mapping.put(local, remote);
          }
        }
      }
      else {
        s.line();
      }
    }
    while (s.hasMoreData());
    return new Info(Collections.unmodifiableSortedMap(mapping), Collections.unmodifiableSortedSet(branches));
  }

  /**
   * Get list of fetch specifications for the configured remote
   *
   * @param project    the project name
   * @param root       the git root
   * @param remoteName the name of the remote
   * @return the configured fetch specifications for remote
   * @throws VcsException if there is a problem with running git
   */
  public static List<String> getFetchSpecs(Project project, VirtualFile root, String remoteName) throws VcsException {
    ArrayList<String> rc = new ArrayList<String>();
    final File rootFile = VfsUtilCore.virtualToIoFile(root);
    @NonNls final File remotesFile = new File(rootFile, GitUtil.DOT_GIT + File.separator + "remotes" + File.separator + remoteName);
    // TODO try branches file?
    if (remotesFile.exists() && !remotesFile.isDirectory()) {
      // try remotes file
      try {
        String text = FileUtil.loadFile(remotesFile, IOUtil.US_ASCII.name());
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
      // try .git/config file
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
    public GitDeprecatedRemote remote() {
      return GitDeprecatedRemote.this;
    }

    /**
     * Get remote branch for the local branch
     *
     * @param localBranchName a local branch name
     * @return a remote branch name or null if the mapping is not found
     */
    @Nullable
    public String getRemoteForLocal(final String localBranchName) {
      if (localBranchName == null) {
        return null;
      }
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

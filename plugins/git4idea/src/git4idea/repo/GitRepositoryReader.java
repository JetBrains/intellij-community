/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.GitReference.BRANCH_NAME_HASHING_STRATEGY;

/**
 * Reads information about the Git repository from Git service files located in the {@code .git} folder.
 * NB: works with {@link File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Git file format.
 *
 * @author Kirill Likhodedov
 */
class GitRepositoryReader {

  private static final Logger LOG = Logger.getInstance(GitRepositoryReader.class);
  private static final Processor<File> NOT_HIDDEN_DIRECTORIES = new Processor<File>() {
    @Override
    public boolean process(File dir) {
      return !isHidden(dir);
    }
  };

  private static Pattern BRANCH_PATTERN = Pattern.compile(" *(?:ref:)? */?((?:refs/heads/|refs/remotes/)?\\S+)");

  @NonNls private static final String REFS_HEADS_PREFIX = "refs/heads/";
  @NonNls private static final String REFS_REMOTES_PREFIX = "refs/remotes/";

  @NotNull private final File          myHeadFile;       // .git/HEAD
  @NotNull private final File          myRefsHeadsDir;   // .git/refs/heads/
  @NotNull private final File          myRefsRemotesDir; // .git/refs/remotes/
  @NotNull private final File          myPackedRefsFile; // .git/packed-refs
  @NotNull private final GitRepositoryFiles myGitFiles;

  GitRepositoryReader(@NotNull GitRepositoryFiles gitFiles) {
    myGitFiles = gitFiles;
    myHeadFile = gitFiles.getHeadFile();
    DvcsUtil.assertFileExists(myHeadFile, ".git/HEAD file not found at " + myHeadFile);
    myRefsHeadsDir = gitFiles.getRefsHeadsFile();
    myRefsRemotesDir = gitFiles.getRefsRemotesFile();
    myPackedRefsFile = gitFiles.getPackedRefsPath();
  }

  @NotNull
  GitBranchState readState(@NotNull Collection<GitRemote> remotes) {
    Map<String, RefInfo> refs = readRefs();
    Pair<Map<GitLocalBranch, Hash>, Map<GitRemoteBranch, Hash>> branches = createBranchesFromData(remotes, refs);
    Map<GitLocalBranch, Hash> localBranches = branches.first;

    //HeadInfo headInfo = readHead(refs);
    HeadInfo headInfo = readHead(refs);
    Repository.State state = readRepositoryState(headInfo);

    GitLocalBranch currentBranch;
    String currentRevision;
    if (!headInfo.isBranch || !localBranches.isEmpty()) {
      currentBranch = findCurrentBranch(headInfo, state, localBranches.keySet());
      currentRevision = getCurrentRevision(headInfo, currentBranch == null ? null : localBranches.get(currentBranch));
      if (currentBranch != null && headInfo.isSymbolicRef()) {
        currentBranch = new GitSymbolicRef(headInfo.symbolicRefName, currentBranch);
      }
    }
    else if (headInfo.content != null) {
      currentBranch = new GitLocalBranch(headInfo.content);
      currentRevision = null;
    }
    else {
      currentBranch = null;
      currentRevision = null;
    }
    if (currentBranch == null && currentRevision == null) {
      LOG.error("Couldn't identify neither current branch nor current revision. .git/HEAD content: [" + headInfo.content + "]");
    }
    return new GitBranchState(currentRevision, currentBranch, state, localBranches, branches.second);
  }

  @Nullable
  private static String getCurrentRevision(@NotNull HeadInfo headInfo, @Nullable Hash currentBranchHash) {
    String currentRevision;
    if (!headInfo.isBranch) {
      currentRevision = headInfo.content;
    }
    else if (currentBranchHash == null) {
      currentRevision = null;
    }
    else {
      currentRevision = currentBranchHash.asString();
    }
    return currentRevision;
  }

  @Nullable
  private GitLocalBranch findCurrentBranch(@NotNull HeadInfo headInfo,
                                           @NotNull Repository.State state,
                                           @NotNull Set<GitLocalBranch> localBranches) {
    final String currentBranchName = findCurrentBranchName(state, headInfo);
    if (currentBranchName == null) {
      return null;
    }
    return ContainerUtil.find(localBranches, new Condition<GitLocalBranch>() {
      @Override
      public boolean value(GitLocalBranch branch) {
        return BRANCH_NAME_HASHING_STRATEGY.equals(branch.getFullName(), currentBranchName);
      }
    });
  }

  @NotNull
  private Repository.State readRepositoryState(@NotNull HeadInfo headInfo) {
    if (isMergeInProgress()) {
      return Repository.State.MERGING;
    }
    if (isRebaseInProgress()) {
      return Repository.State.REBASING;
    }
    if (!headInfo.isBranch) {
      return Repository.State.DETACHED;
    }
    return Repository.State.NORMAL;
  }

  @Nullable
  private String findCurrentBranchName(@NotNull Repository.State state, @NotNull HeadInfo headInfo) {
    String currentBranch = null;
    if (headInfo.isBranch) {
      currentBranch = headInfo.content;
    }
    else if (state == Repository.State.REBASING) {
      currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseApplyDir());
      if (currentBranch == null) {
        currentBranch = readRebaseDirBranchFile(myGitFiles.getRebaseMergeDir());
      }
    }
    return addRefsHeadsPrefixIfNeeded(currentBranch);
  }

  @Nullable
  private static String readRebaseDirBranchFile(@NonNls File rebaseDir) {
    if (rebaseDir.exists()) {
      File headName = new File(rebaseDir, "head-name");
      if (headName.exists()) {
        return DvcsUtil.tryLoadFileOrReturn(headName, null, CharsetToolkit.UTF8);
      }
    }
    return null;
  }

  @Nullable
  private static String addRefsHeadsPrefixIfNeeded(@Nullable String branchName) {
    if (branchName != null && !branchName.startsWith(REFS_HEADS_PREFIX)) {
      return REFS_HEADS_PREFIX + branchName;
    }
    return branchName;
  }

  private boolean isMergeInProgress() {
    return myGitFiles.getMergeHeadFile().exists();
  }

  private boolean isRebaseInProgress() {
    return myGitFiles.getRebaseApplyDir().exists() || myGitFiles.getRebaseMergeDir().exists();
  }

  @NotNull
  private Map<String, String> readPackedBranches() {
    if (!myPackedRefsFile.exists()) {
      return Collections.emptyMap();
    }
    try {
      String content = DvcsUtil.tryLoadFile(myPackedRefsFile, CharsetToolkit.UTF8);
      return ContainerUtil.map2MapNotNull(LineTokenizer.tokenize(content, false), new Function<String, Pair<String, String>>() {
        @Override
        public Pair<String, String> fun(String line) {
          return parsePackedRefsLine(line);
        }
      });
    }
    catch (RepoStateException e) {
      return Collections.emptyMap();
    }
  }

  @NotNull
  private Map<String, RefInfo> readRefs() {
    Map<String, String> data = readBranchRefsFromFiles();
    return resolveRefs(data);
  }

  @NotNull
  private Map<String, String> readBranchRefsFromFiles() {
    Map<String, String> result = ContainerUtil.newHashMap(readPackedBranches()); // reading from packed-refs first to overwrite values by values from unpacked refs
    result.putAll(readFromBranchFiles(myRefsHeadsDir, REFS_HEADS_PREFIX));
    result.putAll(readFromBranchFiles(myRefsRemotesDir, REFS_REMOTES_PREFIX));
    result.remove(REFS_REMOTES_PREFIX + GitUtil.ORIGIN_HEAD);
    return result;
  }

  @NotNull
  private static Pair<Map<GitLocalBranch, Hash>, Map<GitRemoteBranch, Hash>> createBranchesFromData(@NotNull Collection<GitRemote> remotes,
                                                                                                    @NotNull Map<String, RefInfo> data) {
    Map<GitLocalBranch, Hash> localBranches = ContainerUtil.newHashMap();
    Map<GitRemoteBranch, Hash> remoteBranches = ContainerUtil.newHashMap();
    for (Map.Entry<String, RefInfo> entry : data.entrySet()) {
      String refName = entry.getValue().refName;
      Hash hash = entry.getValue().hash;
      if (refName.startsWith(REFS_HEADS_PREFIX)) {
        localBranches.put(fromRefInfo(entry.getValue()), hash);
      }
      else if (refName.startsWith(REFS_REMOTES_PREFIX)) {
        GitRemoteBranch remoteBranch = parseRemoteBranch(refName, remotes);
        if (remoteBranch != null) {
          remoteBranches.put(remoteBranch, hash);
        }
      }
      else {
        LOG.warn("Unexpected ref format: " + refName);
      }
    }
    return Pair.create(localBranches, remoteBranches);
  }

  private static GitLocalBranch fromRefInfo(RefInfo refInfo) {
    return refInfo.isSymbolicRef ?
           new GitSymbolicRef(refInfo.refName, new GitLocalBranch(refInfo.referencedRef)) :
           new GitLocalBranch(refInfo.refName);
  }

  @Nullable
  private static String loadHashFromBranchFile(@NotNull File branchFile) {
    return DvcsUtil.tryLoadFileOrReturn(branchFile, null);
  }

  @NotNull
  private static Map<String, String> readFromBranchFiles(@NotNull final File refsRootDir, @NotNull final String prefix) {
    if (!refsRootDir.exists()) {
      return Collections.emptyMap();
    }
    final Map<String, String> result = new HashMap<>();
    FileUtil.processFilesRecursively(refsRootDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (!file.isDirectory() && !isHidden(file)) {
          String relativePath = FileUtil.getRelativePath(refsRootDir, file);
          if (relativePath != null) {
            String branchName = prefix + FileUtil.toSystemIndependentName(relativePath);
            String hash = loadHashFromBranchFile(file);
            if (hash != null) {
              result.put(branchName, hash);
            }
          }
        }
        return true;
      }
    }, NOT_HIDDEN_DIRECTORIES);
    return result;
  }

  private static boolean isHidden(@NotNull File file) {
    return file.getName().startsWith(".");
  }

  @Nullable
  private static GitRemoteBranch parseRemoteBranch(@NotNull String fullBranchName,
                                                   @NotNull Collection<GitRemote> remotes) {
    String stdName = GitBranchUtil.stripRefsPrefix(fullBranchName);

    int slash = stdName.indexOf('/');
    if (slash == -1) { // .git/refs/remotes/my_branch => git-svn
      return new GitSvnRemoteBranch(fullBranchName);
    }
    else {
      GitRemote remote;
      String remoteName;
      String branchName;
      do {
        remoteName = stdName.substring(0, slash);
        branchName = stdName.substring(slash + 1);
        remote = GitUtil.findRemoteByName(remotes, remoteName);
        slash = stdName.indexOf('/', slash + 1);
      } while(remote == null && slash >= 0);

      if (remote == null) {
        // user may remove the remote section from .git/config, but leave remote refs untouched in .git/refs/remotes
        LOG.debug(String.format("No remote found with the name [%s]. All remotes: %s", remoteName, remotes));
        GitRemote fakeRemote = new GitRemote(remoteName, ContainerUtil.<String>emptyList(), Collections.<String>emptyList(),
                                             Collections.<String>emptyList(), Collections.<String>emptyList());
        return new GitStandardRemoteBranch(fakeRemote, branchName);
      }
      return new GitStandardRemoteBranch(remote, branchName);
    }
  }

  @NotNull
  private HeadInfo readHead(Map<String, RefInfo> refs) {
    String headContent;
    try {
      headContent = DvcsUtil.tryLoadFile(myHeadFile, CharsetToolkit.UTF8);
    }
    catch (RepoStateException e) {
      LOG.error(e);
      return new HeadInfo(false, null);
    }

    Hash hash = parseHash(headContent);
    if (hash != null) {
      return new HeadInfo(false, headContent);
    }

    String target = getTarget(headContent);
    if (target != null) {
      if (!refs.containsKey(target)) {
        LOG.error(new RepoStateException("Unknown ref in HEAD: " + target));
        return new HeadInfo(false, null);
      } else {
        RefInfo last = refs.get(target);
        while (last.isSymbolicRef) {
          last = refs.get(last.referencedRef);
        }
        return new HeadInfo(true, last.refName, target.equals(last.refName) ? null : target);
      }
    }
    LOG.error(new RepoStateException("Invalid format of the .git/HEAD file: [" + headContent + "]")); // including "refs/tags/v1"
    return new HeadInfo(false, null);
  }

  /**
   * Parses a line from the .git/packed-refs file returning a pair of hash and ref name.
   * Comments and tags are ignored, and null is returned.
   * Incorrectly formatted lines are ignored, a warning is printed to the log, null is returned.
   * A line indicating a hash which an annotated tag (specified in the previous line) points to, is ignored: null is returned.
   */
  @Nullable
  private static Pair<String, String> parsePackedRefsLine(@NotNull String line) {
    line = line.trim();
    if (line.isEmpty()) {
      return null;
    }
    char firstChar = line.charAt(0);
    if (firstChar == '#') { // ignoring comments
      return null;
    }
    if (firstChar == '^') {
      // ignoring the hash which an annotated tag above points to
      return null;
    }
    String hash = null;
    int i;
    for (i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (!Character.isLetterOrDigit(c)) {
        hash = line.substring(0, i);
        break;
      }
    }
    if (hash == null) {
      LOG.warn("Ignoring invalid packed-refs line: [" + line + "]");
      return null;
    }

    String branch = null;
    int start = i;
    if (start < line.length() && line.charAt(start++) == ' ') {
      for (i = start; i < line.length(); i++) {
        char c = line.charAt(i);
        if (Character.isWhitespace(c)) {
          break;
        }
      }
      branch = line.substring(start, i);
    }

    if (branch == null || !branch.startsWith(REFS_HEADS_PREFIX) && !branch.startsWith(REFS_REMOTES_PREFIX)) {
      return null;
    }
    return Pair.create(shortBuffer(branch), shortBuffer(hash.trim()));
  }

  @NotNull
  private static String shortBuffer(String raw) {
    return new String(raw);
  }

  @NotNull
  private static Map<String, RefInfo> resolveRefs(@NotNull Map<String, String> data) {
    final Map<String, RefInfo> resolved = getResolvedHashes(data);
    Map<String, String> unresolved = ContainerUtil.filter(data, new Condition<String>() {
      @Override
      public boolean value(String refName) {
        return !resolved.containsKey(refName);
      }
    });

    boolean progressed = true;
    while (progressed && !unresolved.isEmpty()) {
      progressed = false;
      for (Iterator<Map.Entry<String, String>> iterator = unresolved.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<String, String> entry = iterator.next();
        String refName = entry.getKey();
        String refValue = entry.getValue();
        String link = getTarget(refValue);
        if (link != null) {
          if (duplicateEntry(resolved, refName, refValue)) {
            iterator.remove();
          }
          else if (!resolved.containsKey(link)) {
            LOG.debug("Unresolved symbolic link [" + refName + "] pointing to [" + refValue + "]"); // transitive link
          }
          else {
            RefInfo referencedRef = resolved.get(link);
            resolved.put(refName, new RefInfo(refName, referencedRef.hash, getRightRefName(referencedRef)));
            iterator.remove();
            progressed = true;
          }
        }
        else {
          LOG.warn("Unexpected record [" + refName + "] -> [" + refValue + "]");
          iterator.remove();
        }
      }
    }
    if (!unresolved.isEmpty()) {
      LOG.warn("Cyclic symbolic links among .git/refs: " + unresolved);
    }
    return resolved;
  }

  private static String getRightRefName(RefInfo refInfo) {
    return refInfo.referencedRef != null ? refInfo.referencedRef : refInfo.refName;
  }

  @NotNull
  private static Map<String, RefInfo> getResolvedHashes(@NotNull Map<String, String> data) {
    Map<String, RefInfo> resolved = ContainerUtil.newHashMap();
    for (Map.Entry<String, String> entry : data.entrySet()) {
      String refName = entry.getKey();
      Hash hash = parseHash(entry.getValue());
      if (hash != null && !duplicateEntry(resolved, refName, hash)) {
        resolved.put(refName, new RefInfo(refName, hash));
      }
    }
    return resolved;
  }

  @Nullable
  private static String getTarget(@NotNull String refName) {
    Matcher matcher = BRANCH_PATTERN.matcher(refName);
    if (!matcher.matches()) {
      return null;
    }
    String target = matcher.group(1);
    if (!target.startsWith(REFS_HEADS_PREFIX) && !target.startsWith(REFS_REMOTES_PREFIX)) {
      target = REFS_HEADS_PREFIX + target;
    }
    return target;
  }

  @Nullable
  private static Hash parseHash(@NotNull String value) {
    try {
      return HashImpl.build(value);
    }
    catch (Exception e) {
      return null;
    }
  }

  private static boolean duplicateEntry(@NotNull Map<String, RefInfo> resolved, @NotNull String refName, @NotNull Object newValue) {
    if (resolved.containsKey(refName)) {
      LOG.error("Duplicate entry for [" + refName + "]. resolved: [" + resolved.get(refName).hash.asString() + "], current: " + newValue + "]");
      return true;
    }
    return false;
  }

  /**
   * Container to hold two information items: current .git/HEAD value and is Git on branch.
   */
  private static class HeadInfo {
    @Nullable private final String content;
    private final boolean isBranch;
    @Nullable private final String symbolicRefName;

    HeadInfo(boolean branch, @Nullable String content) {
      this(branch, content, null);
    }

    HeadInfo(boolean branch, @Nullable String content, @Nullable String symbolicRefName) {
      isBranch = branch;
      this.content = content;
      this.symbolicRefName = symbolicRefName;
    }

    boolean isSymbolicRef() {
      return symbolicRefName != null;
    }
  }

  private static class RefInfo {
    private final String refName;
    private final Hash hash;
    private final boolean isSymbolicRef;
    @Nullable private final String referencedRef;

    RefInfo(String refName, Hash hash, @NotNull String referencedRef) {
      this.refName = refName;
      this.hash = hash;
      this.isSymbolicRef = true;
      this.referencedRef = referencedRef;
    }

    public RefInfo(String refName, Hash hash) {
      this.refName = refName;
      this.hash = hash;
      this.isSymbolicRef = false;
      this.referencedRef = null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RefInfo info = (RefInfo)o;

      if (refName != null ? !refName.equals(info.refName) : info.refName != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return refName != null ? refName.hashCode() : 0;
    }
  }
}

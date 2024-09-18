// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitTag;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;

public final class GitRefUtil {
  private static final Logger LOG = Logger.getInstance(GitRefUtil.class);
  private static final Pattern BRANCH_PATTERN = Pattern.compile(" *(?:ref:)? */?((?:refs/heads/|refs/remotes/)?\\S+)\\s*");

  @Contract("null -> null;!null -> !null")
  public static @Nullable String addRefsHeadsPrefixIfNeeded(@Nullable String branchName) {
    if (branchName != null && !branchName.startsWith(REFS_HEADS_PREFIX)) {
      return REFS_HEADS_PREFIX + branchName;
    }
    return branchName;
  }

  /**
   * @return pairs [branchName, hash], only for local and remote branches
   * @see #parseRefsLine
   */
  @ApiStatus.Internal
  public static @Nullable Pair<String, String> parseBranchesLine(@NotNull String line) {
    var parsedRef = parseRefsLine(line);
    if (parsedRef == null) {
      return null;
    }
    var branch = parsedRef.first;
    if (!branch.startsWith(REFS_HEADS_PREFIX) && !branch.startsWith(REFS_REMOTES_PREFIX)) {
      return null;
    }
    return parsedRef;
  }

  /**
   * Parses a line with a pair of hash and ref name e.g. see the .git/packed-refs file.
   * Comments and tags are ignored, and null is returned.
   * Incorrectly formatted lines are ignored, a warning is printed to the log, null is returned.
   * A line indicating a hash which an annotated tag (specified in the previous line) points to, is ignored: null is returned.
   */
  public static @Nullable Pair<String, String> parseRefsLine(@NotNull String line) {
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
    if (Character.isWhitespace(line.charAt(start++))) {
      for (i = start; i < line.length(); i++) {
        char c = line.charAt(i);
        if (Character.isWhitespace(c)) {
          break;
        }
      }
      branch = line.substring(start, i);
    }

    if (branch == null) {
      return null;
    }
    return Pair.create(branch, hash.trim());
  }

  public static @NotNull Map<String, String> readFromRefsFiles(final @NotNull File refsRootDir,
                                                               final @NotNull String prefix,
                                                               GitRepositoryFiles repositoryFiles) {

    HashMap<String, String> result = new HashMap<>();
    BiConsumer<String, String> collectingConsumer = (s, s2) -> result.put(s, s2);
    readFromRefsFiles(refsRootDir, prefix, repositoryFiles, collectingConsumer);
    return result;
  }

  @Nullable
  public static GitReference getCurrentReference(GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch != null) {
      return currentBranch;
    }
    else {
      String currentRevision = repository.getCurrentRevision();
      if (currentRevision != null) {
        return repository.getTagHolder().getTag(currentRevision);
      }
    }
    return null;
  }

  @Nullable
  public static GitTag getCurrentTag(GitRepository repository) {
    if (repository.getState() != Repository.State.DETACHED) return null;

    GitReference currentRef = getCurrentReference(repository);
    return currentRef instanceof GitTag ? (GitTag)currentRef : null;
  }

  public static void readFromRefsFiles(final @NotNull File refsRootDir,
                                       final @NotNull String prefix,
                                       GitRepositoryFiles repositoryFiles,
                                       BiConsumer<String, String> consumer) {
    if (!refsRootDir.exists()) {
      return;
    }
    Ref<Boolean> couldNotLoadFile = Ref.create(false);
    FileUtil.processFilesRecursively(refsRootDir, file -> {
      if (!file.isDirectory() && !isHidden(file)) {
        String relativePath = FileUtil.getRelativePath(refsRootDir, file);
        if (relativePath != null) {
          String branchName = prefix + FileUtil.toSystemIndependentName(relativePath);
          boolean isBranchNameValid = GitRefNameValidator.getInstance().checkInput(branchName);
          if (isBranchNameValid) {
            String hash = loadHashFromBranchFile(file);
            if (hash != null) {
              consumer.accept(branchName, hash);
            }
            else {
              couldNotLoadFile.set(true);
            }
          }
        }
      }
      return true;
    }, dir -> !isHidden(dir));
    if (couldNotLoadFile.get()) {
      logDebugAllRefsFiles(repositoryFiles);
    }
  }

  private static boolean isHidden(@NotNull File file) {
    return file.getName().startsWith(".");
  }

  public static @Nullable String loadHashFromBranchFile(@NotNull File branchFile) {
    return DvcsUtil.tryLoadFileOrReturn(branchFile, null);
  }

  public static void logDebugAllRefsFiles(GitRepositoryFiles gitRepositoryFiles) {
    File refsHeadsFile = gitRepositoryFiles.getRefsHeadsFile();
    File refsRemotesFile = gitRepositoryFiles.getRefsRemotesFile();
    File refsTagsFile = gitRepositoryFiles.getRefsTagsFile();

    LOG.debug("Logging .git/refs files. " +
              ".git/refs/heads " + (refsHeadsFile.exists() ? "exists" : "doesn't exist") +
              ".git/refs/remotes " + (refsRemotesFile.exists() ? "exists" : "doesn't exist") +
              ".git/refs/tags " + (refsTagsFile.exists() ? "exists" : "doesn't exist"));
    if (LOG.isDebugEnabled()) {
      logDebugAllFilesIn(refsHeadsFile);
      logDebugAllFilesIn(refsRemotesFile);
      logDebugAllFilesIn(refsTagsFile);
      File packedRefsPath = gitRepositoryFiles.getPackedRefsPath();
      if (packedRefsPath.exists()) {
        try {
          LOG.debug("packed-refs file content: [\n" + FileUtil.loadFile(packedRefsPath) + "\n]");
        }
        catch (IOException e) {
          LOG.debug("Couldn't load the file " + packedRefsPath, e);
        }
      }
      else {
        LOG.debug("The file " + packedRefsPath + " doesn't exist.");
      }
    }
  }

  public static void logDebugAllFilesIn(@NotNull File dir) {
    List<String> paths = new ArrayList<>();
    FileUtil.processFilesRecursively(dir, (file) -> {
      if (!file.isDirectory()) paths.add(FileUtil.getRelativePath(dir, file));
      return true;
    });
    LOG.debug("Files in " + dir + ": " + paths);
  }

  static @NotNull Map<String, Hash> resolveRefs(@NotNull Map<String, String> data) {
    final Map<String, Hash> resolved = getResolvedHashes(data);
    Map<String, String> unresolved = ContainerUtil.filter(data, refName -> !resolved.containsKey(refName));

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
            Hash targetValue = resolved.get(link);
            resolved.put(refName, targetValue);
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


  public static @NotNull Map<String, Hash> getResolvedHashes(@NotNull Map<String, String> data) {
    Map<String, Hash> resolved = new HashMap<>();
    for (Map.Entry<String, String> entry : data.entrySet()) {
      String refName = entry.getKey();
      Hash hash = parseHash(entry.getValue());
      if (hash != null && !duplicateEntry(resolved, refName, hash)) {
        resolved.put(refName, hash);
      }
    }
    return resolved;
  }

  static @Nullable String getTarget(@NotNull String refName) {
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

  static @Nullable Hash parseHash(@NotNull String value) {
    try {
      return HashImpl.build(value);
    }
    catch (Exception e) {
      return null;
    }
  }

  static boolean duplicateEntry(@NotNull Map<String, Hash> resolved, @NotNull String refName, @NotNull Object newValue) {
    if (resolved.containsKey(refName)) {
      LOG.error("Duplicate entry for [" + refName + "]. resolved: [" + resolved.get(refName).asString() + "], current: " + newValue + "]");
      return true;
    }
    return false;
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;

public class GitRefUtil {
  private static final Logger LOG = Logger.getInstance(GitRefUtil.class);
  private static final Pattern BRANCH_PATTERN = Pattern.compile(" *(?:ref:)? */?((?:refs/heads/|refs/remotes/)?\\S+)");

  @Nullable
  public static String addRefsHeadsPrefixIfNeeded(@Nullable String branchName) {
    if (branchName != null && !branchName.startsWith(REFS_HEADS_PREFIX)) {
      return REFS_HEADS_PREFIX + branchName;
    }
    return branchName;
  }

  /**
   * Parses a line with a pair of hash and ref name e.g. see the .git/packed-refs file.
   * Comments and tags are ignored, and null is returned.
   * Incorrectly formatted lines are ignored, a warning is printed to the log, null is returned.
   * A line indicating a hash which an annotated tag (specified in the previous line) points to, is ignored: null is returned.
   */
  @Nullable
  public static Pair<String, String> parseRefsLine(@NotNull String line) {
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
    if (start < line.length() && Character.isWhitespace(line.charAt(start++))) {
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
  static Map<String, Hash> resolveRefs(@NotNull Map<String, String> data) {
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


  @NotNull
  public static Map<String, Hash> getResolvedHashes(@NotNull Map<String, String> data) {
    Map<String, Hash> resolved = ContainerUtil.newHashMap();
    for (Map.Entry<String, String> entry : data.entrySet()) {
      String refName = entry.getKey();
      Hash hash = parseHash(entry.getValue());
      if (hash != null && !duplicateEntry(resolved, refName, hash)) {
        resolved.put(refName, hash);
      }
    }
    return resolved;
  }

  @Nullable
  static String getTarget(@NotNull String refName) {
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
  static Hash parseHash(@NotNull String value) {
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

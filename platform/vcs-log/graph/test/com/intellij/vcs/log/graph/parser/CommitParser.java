package com.intellij.vcs.log.graph.parser;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParser {

  public static final String SEPARATOR = "|-";

  public static int nextSeparatorIndex(@NotNull String line, int startIndex) {
    int nextIndex = line.indexOf(SEPARATOR, startIndex);
    if (nextIndex == -1) {
      throw new IllegalArgumentException("not found separator \"" + SEPARATOR + "\", with startIndex=" + startIndex +
                                         ", in line: " + line);
    }
    return nextIndex;
  }

  /**
   * @param line input format:
   *             ab123|-adada 193 352
   *             123|-             // no parent
   */
  @NotNull
  public static Pair<String, String[]> parseCommitParents(@NotNull String line) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    String commitHashStr = line.substring(0, separatorIndex);

    String parentHashStr = line.substring(separatorIndex + 2, line.length());
    String[] parentsHashes = parentHashStr.split("\\s");
    return Pair.create(commitHashStr, parentsHashes);
  }

  @NotNull
  public static GraphCommit<String> parseCommitParentsAsString(@NotNull String line) {
    Pair<String, String[]> stringPair = parseCommitParents(line);
    return SimpleCommit.asStringCommit(stringPair.first, stringPair.second);
  }

  @NotNull
  public static GraphCommit<Integer> parseCommitParentsAsInteger(@NotNull String line) {
    Pair<String, String[]> stringPair = parseCommitParents(line);
    return SimpleCommit.asIntegerCommit(stringPair.first, stringPair.second);
  }

  public static int createHash(@NotNull String s) {
    return Integer.parseInt(s, 16);
  }

  public static List<String> toLines(@NotNull String in) {
    String[] split = in.split("\n");
    List<String> result = new ArrayList<>();
    for (String line : split) {
      if (!line.isEmpty()) {
        result.add(line);
      }
    }
    return result;
  }

  @NotNull
  public static List<GraphCommit<String>> parseStringCommitList(@NotNull String input) {
    List<GraphCommit<String>> vcsCommitParents = new ArrayList<>();
    for (String line : toLines(input)) {
      vcsCommitParents.add(CommitParser.parseCommitParentsAsString(line));
    }
    return vcsCommitParents;
  }

  @NotNull
  public static List<GraphCommit<Integer>> parseIntegerCommitList(@NotNull String input) {
    List<GraphCommit<Integer>> vcsCommitParents = new ArrayList<>();
    for (String line : toLines(input)) {
      vcsCommitParents.add(CommitParser.parseCommitParentsAsInteger(line));
    }
    return vcsCommitParents;
  }

}

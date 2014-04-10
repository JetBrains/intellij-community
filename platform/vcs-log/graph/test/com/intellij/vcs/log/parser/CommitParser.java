package com.intellij.vcs.log.parser;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitParser {

  public static int nextSeparatorIndex(@NotNull String line, int startIndex) {
    int nextIndex = line.indexOf("|-", startIndex);
    if (nextIndex == -1) {
      throw new IllegalArgumentException("not found separator \"|-\", with startIndex=" + startIndex +
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
    return new Pair<String, String[]>(commitHashStr, parentsHashes);
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

}

package com.intellij.vcs.log.parser;

import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.SimpleCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
  public static GraphCommit parseCommitParents(@NotNull String line) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    String commitHashStr = line.substring(0, separatorIndex);
    int commitHash = createHash(commitHashStr);

    String parentHashStr = line.substring(separatorIndex + 2, line.length());
    String[] parentsHashes = parentHashStr.split("\\s");
    List<Integer> hashes = new ArrayList<Integer>(parentsHashes.length);
    for (String aParentsStr : parentsHashes) {
      if (aParentsStr.length() > 0) {
        hashes.add(createHash(aParentsStr));
      }
    }
    return new SimpleCommit(commitHash, ArrayUtil.toIntArray(hashes));
  }

  public static int createHash(@NotNull String s) {
    return Integer.parseInt(s, 16);
  }

}

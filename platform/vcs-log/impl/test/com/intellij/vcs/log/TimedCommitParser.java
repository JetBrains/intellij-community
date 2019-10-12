// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.parser.CommitParser;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.TimedVcsCommitImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimedCommitParser {

  /**
   * @param line 1231423|-adada|-193 adf45
   *             timestamp|-hash commit|-parent hashes
   */
  @NotNull
  private static TimedVcsCommit parseTimestampParentHashes(@NotNull String line) {
    int firstSeparatorIndex = CommitParser.nextSeparatorIndex(line, 0);
    long timestamp;
    try {
      String timestampStr = line.substring(0, firstSeparatorIndex);
      if (timestampStr.isEmpty()) {
        timestamp = 0;
      }
      else {
        timestamp = Long.parseLong(timestampStr);
      }
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("bad timestamp in line: " + line);
    }
    GraphCommit<Integer> commit = CommitParser.parseCommitParentsAsInteger(line.substring(firstSeparatorIndex + 2));
    List<Hash> parents = new ArrayList<>();
    for (int p : commit.getParents()) {
      parents.add(intToHash(p));
    }
    return new TimedVcsCommitImpl(intToHash(commit.getId()), parents, timestamp);
  }

  private static Hash intToHash(int index) {
    return HashImpl.build(Integer.toHexString(index));
  }

  @NotNull
  public static List<TimedVcsCommit> log(@NotNull List<String> commits) {
    return ContainerUtil.map(commits, TimedCommitParser::parseTimestampParentHashes);
  }

  @NotNull
  public static List<TimedVcsCommit> log(@NotNull String... commits) {
    return log(Arrays.asList(commits));
  }
}

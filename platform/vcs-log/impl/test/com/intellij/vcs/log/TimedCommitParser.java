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
package com.intellij.vcs.log;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class TimedCommitParser {

  /**
   * @param line 1231423|-adada|-193 adf45
   *             timestamp|-hash commit|-parent hashes
   */
  @NotNull
  public static TimedVcsCommit parseTimestampParentHashes(@NotNull String line) {
    int firstSeparatorIndex = CommitParser.nextSeparatorIndex(line, 0);
    String timestampStr = line.substring(0, firstSeparatorIndex);
    long timestamp;
    try {
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
    GraphCommit commit = CommitParser.parseCommitParents(line.substring(firstSeparatorIndex + 2));
    List<Hash> parents = ContainerUtil.newArrayList();
    for (int p : commit.getParentIndices()) {
      parents.add(intToHash(p));
    }
    return new SimpleTimedVcsCommit(intToHash(commit.getIndex()), parents, timestamp);
  }

  private static Hash intToHash(int index) {
    return new SimpleHash(Integer.toHexString(index));
  }

  @NotNull
  public static List<TimedVcsCommit> log(@NotNull String... commits) {
    return ContainerUtil.map(Arrays.asList(commits), new Function<String, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(String commit) {
        return parseTimestampParentHashes(commit);
      }
    });
  }

}

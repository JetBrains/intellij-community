package com.intellij.vcs.log.graph.parser;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitListParser {

  @NotNull
  public static List<GraphCommit<String>> parseStringCommitList(@NotNull String input) {
    BufferedReader bufferedReader = new BufferedReader(new StringReader(input));
    try {
      String line;
      List<GraphCommit<String>> vcsCommitParentses = ContainerUtil.newArrayList();
      while ((line = bufferedReader.readLine()) != null) {
        vcsCommitParentses.add(CommitParser.parseCommitParentsAsString(line));
      }
      return vcsCommitParentses;
    }
    catch (IOException e) {
      throw new IllegalStateException();
    }
  }

  @NotNull
  public static List<GraphCommit<Integer>> parseIntegerCommitList(@NotNull String input) {
    BufferedReader bufferedReader = new BufferedReader(new StringReader(input));
    try {
      String line;
      List<GraphCommit<Integer>> vcsCommitParentses = ContainerUtil.newArrayList();
      while ((line = bufferedReader.readLine()) != null) {
        vcsCommitParentses.add(CommitParser.parseCommitParentsAsInteger(line));
      }
      return vcsCommitParentses;
    }
    catch (IOException e) {
      throw new IllegalStateException();
    }
  }

}

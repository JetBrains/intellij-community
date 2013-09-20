package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.data.rebase.FakeCommitParents;

import java.util.List;

public class FakeCommitsInfo {

  public final List<FakeCommitParents> commits;
  public final Node base;
  public final int insertAbove;
  public final VcsRef resultRef;
  public final VcsRef subjectRef;

  public FakeCommitsInfo(List<FakeCommitParents> commits, Node base, int insertAbove, VcsRef resultRef, VcsRef subjectRef) {
    this.commits = commits;
    this.base = base;
    this.insertAbove = insertAbove;
    this.resultRef = resultRef;
    this.subjectRef = subjectRef;
  }
}

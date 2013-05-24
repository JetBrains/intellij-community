package org.hanuna.gitalk.data.impl;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;
import java.util.Set;

public class FakeCommitsInfo {

  public final List<FakeCommitParents> commits;
  public final Set<Hash> commitsToHide;
  public final Node base;
  public final Ref resultRef;
  public final Ref subjectRef;

  public FakeCommitsInfo(List<FakeCommitParents> commits, Set<Hash> commitsToHide, Node base, Ref resultRef, Ref subjectRef) {
    this.commits = commits;
    this.commitsToHide = commitsToHide;
    this.base = base;
    this.resultRef = resultRef;
    this.subjectRef = subjectRef;
  }
}

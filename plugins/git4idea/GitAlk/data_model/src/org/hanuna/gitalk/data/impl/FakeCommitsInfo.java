package org.hanuna.gitalk.data.impl;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;

public class FakeCommitsInfo {

  public final List<FakeCommitParents> commits;
  public final Node base;
  public final Ref resultRef;

  public FakeCommitsInfo(List<FakeCommitParents> commits, Node base, Ref resultRef) {
    this.commits = commits;
    this.base = base;
    this.resultRef = resultRef;
  }
}

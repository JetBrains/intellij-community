package org.hanuna.gitalk.log.parser;

import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;

import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitParents extends CommitParents {

  public SimpleCommitParents(Hash commitHash, List<Hash> parentHashes) {
    super(commitHash, parentHashes);
  }

}

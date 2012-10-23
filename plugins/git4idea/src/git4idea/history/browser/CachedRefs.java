/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MultiMap;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.history.wholeTree.AbstractHash;

import java.util.Collection;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/1/11
 * Time: 10:20 PM
 */
public class CachedRefs implements SymbolicRefsI {
  private GitBranchesCollection myCollection;
  private GitBranch myCurrentBranch;
  private String myTrackedRemoteName;
  private String myUsername;
  private AbstractHash myHeadHash;

  private TreeSet<String> myLocalBranches;
  private TreeSet<String> myRemoteBranches;
  // hash to
  private final MultiMap<String, Pair<SymbolicRefs.Kind, String>> myRefsMap;

  public CachedRefs() {
    myRefsMap = new MultiMap<String, Pair<SymbolicRefs.Kind, String>>();
  }

  public void setCurrentBranch(GitBranch currentBranch) {
    myCurrentBranch = currentBranch;
  }

  public void setCollection(GitBranchesCollection collection) {
    myCollection = collection;
    /*GitBranch branch = myCollection.getCurrentBranch();
    if (branch != null) {
      myRefsMap.putValue(branch.getHash(), new Pair<SymbolicRefs.Kind, String>(SymbolicRefs.Kind.LOCAL, branch.getName()));
    }
    Collection<GitBranch> branches = myCollection.getLocalBranches();
    for (GitBranch localBranch : branches) {
      myRefsMap.putValue(localBranch.getHash(), new Pair<SymbolicRefs.Kind, String>(SymbolicRefs.Kind.LOCAL, localBranch.getName()));
    }
    Collection<GitBranch> remoteBranches = myCollection.getRemoteBranches();
    for (GitBranch remoteBranch : remoteBranches) {
      myRefsMap.putValue(remoteBranch.getHash(), new Pair<SymbolicRefs.Kind, String>(SymbolicRefs.Kind.REMOTE, remoteBranch.getName()));
    }*/
  }
  
  /*public List<Pair<SymbolicRefs.Kind, String>> getRefsForCommit(final GitCommit gitCommit) {
    final List<Pair<SymbolicRefs.Kind, String>> result = new ArrayList<Pair<SymbolicRefs.Kind, String>>();
    Collection<Pair<SymbolicRefs.Kind, String>> pairs = myRefsMap.get(gitCommit.getHash().getValue());
    if (pairs != null) {
      result.addAll(pairs);
    }
    if (myHeadHash != null && myHeadHash.equals(gitCommit.getShortHash())) {
      result.add(new Pair<SymbolicRefs.Kind, String>(SymbolicRefs.Kind.TAG, "HEAD"));
    }
    return result;
  }*/

  public void setTrackedRemoteName(String trackedRemoteName) {
    myTrackedRemoteName = trackedRemoteName;
  }

  public void setUsername(String username) {
    myUsername = username;
  }

  public void setHeadHash(AbstractHash headHash) {
    myHeadHash = headHash;
  }

  public TreeSet<String> getLocalBranches() {
    if (myLocalBranches == null) {
      Collection<GitLocalBranch> branches = myCollection.getLocalBranches();
      myLocalBranches = new TreeSet<String>();
      for (GitLocalBranch branch : branches) {
        myLocalBranches.add(branch.getName());
      }
    }
    return myLocalBranches;
  }

  public TreeSet<String> getRemoteBranches() {
    if (myRemoteBranches == null) {
      Collection<GitRemoteBranch> branches = myCollection.getRemoteBranches();
      myRemoteBranches = new TreeSet<String>();
      for (GitRemoteBranch branch : branches) {
        myRemoteBranches.add(branch.getName());
      }
    }
    return myRemoteBranches;
  }


  @Override
  public String getCurrentName() {
    return myCurrentBranch == null ? null : myCurrentBranch.getName();
  }

  @Override
  public GitBranch getCurrent() {
    return myCurrentBranch;
  }

  @Override
  public SymbolicRefs.Kind getKind(String s) {
    if (getLocalBranches().contains(s)) return SymbolicRefs.Kind.LOCAL;
    if (getRemoteBranches().contains(s)) return SymbolicRefs.Kind.REMOTE;
    return SymbolicRefs.Kind.TAG;
  }

  @Override
  public String getTrackedRemoteName() {
    return myTrackedRemoteName;
  }

  @Override
  public String getUsername() {
    return myUsername;
  }

  @Override
  public AbstractHash getHeadHash() {
    return myHeadHash;
  }

  public Collection<? extends GitBranch> getLocal() {
    return myCollection.getLocalBranches();
  }

  public Collection<? extends GitBranch> getRemote() {
    return myCollection.getRemoteBranches();
  }
}

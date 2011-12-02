/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.browser;

import git4idea.GitBranch;
import git4idea.history.wholeTree.AbstractHash;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.TreeSet;

/**
 * @author irengrig
 */
public class SymbolicRefs implements SymbolicRefsI {
  private GitBranch myCurrent;
  private final TreeSet<String> myLocalBranches;
  private final TreeSet<String> myRemoteBranches;
  private String myTrackedRemoteName;
  private String myUsername;
  private AbstractHash myHeadHash;

  public SymbolicRefs() {
    myLocalBranches = new TreeSet<String>();
    myRemoteBranches = new TreeSet<String>();
  }

  public void addRemote(final String branch) {
    myRemoteBranches.add(branch);
  }

  public void addLocal(final String branch) {
    myLocalBranches.add(branch);
  }

  public void addLocals(final Collection<String> value) {
    myLocalBranches.addAll(value);
  }

  public void addRemotes(final Collection<String> value) {
    myRemoteBranches.addAll(value);
  }

  public TreeSet<String> getLocalBranches() {
    return myLocalBranches;
  }

  public TreeSet<String> getRemoteBranches() {
    return myRemoteBranches;
  }

  @Override
  @Nullable
  public String getCurrentName() {
    return myCurrent == null ? null : myCurrent.getName();
  }

  @Override
  public GitBranch getCurrent() {
    return myCurrent;
  }

  public void setCurrent(GitBranch current) {
    myCurrent = current;
  }

  @Override
  public Kind getKind(final String s) {
    if (myLocalBranches.contains(s)) return Kind.LOCAL;
    if (myRemoteBranches.contains(s)) return Kind.REMOTE;
    return Kind.TAG;
  }

  public void clear() {
    myLocalBranches.clear();
    myRemoteBranches.clear();
  }

  public void setTrackedRemote(String trackedRemoteName) {
    myTrackedRemoteName = trackedRemoteName;
  }

  @Override
  public String getTrackedRemoteName() {
    return myTrackedRemoteName;
  }

  @Override
  public String getUsername() {
    return myUsername;
  }

  public void setUsername(String username) {
    myUsername = username;
  }

  public void setHead(AbstractHash hash) {
    myHeadHash = hash;
  }

  @Override
  public AbstractHash getHeadHash() {
    return myHeadHash;
  }

  public static enum Kind {
    TAG,
    LOCAL,
    REMOTE
  }
}

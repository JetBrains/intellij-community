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

import java.util.TreeSet;

@Deprecated
public class SymbolicRefs implements SymbolicRefsI {
  private GitBranch myCurrent;
  private final TreeSet<String> myLocalBranches;
  private final TreeSet<String> myRemoteBranches;
  private String myUsername;

  public SymbolicRefs() {
    myLocalBranches = new TreeSet<>();
    myRemoteBranches = new TreeSet<>();
  }

  public TreeSet<String> getLocalBranches() {
    return myLocalBranches;
  }

  public TreeSet<String> getRemoteBranches() {
    return myRemoteBranches;
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

  @Override
  public String getUsername() {
    return myUsername;
  }

  public void setUsername(String username) {
    myUsername = username;
  }

  public enum Kind {
    TAG,
    LOCAL,
    REMOTE
  }
}

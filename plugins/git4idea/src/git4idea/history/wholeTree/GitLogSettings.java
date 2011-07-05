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
package git4idea.history.wholeTree;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author irengrig
 *         Date: 7/1/11
 *         Time: 1:11 PM
 */
@State(
  name = "GitLogSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GitLogSettings implements PersistentStateComponent<GitLogSettings.MyState> {
  private MyState myState = new MyState();

  public boolean setIfChanged(ArrayList<String> local, ArrayList<String> remote) {
    if (Comparing.haveEqualElements(myState.myContainedLocalBranches, local) && Comparing.haveEqualElements(myState.myContainedRemoteBranches, remote)) {
      return false;
    }
    setLocal(local);
    setRemote(remote);
    return true;
  }

  public static class MyState {
    public List<String> myContainedLocalBranches = new ArrayList<String>();
    public List<String> myContainedRemoteBranches = new ArrayList<String>();
  }

  @Override
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(MyState state) {
    myState = state;
  }

  public static GitLogSettings getInstance(final Project project) {
    return ServiceManager.getService(project, GitLogSettings.class);
  }

  public Set<String> getLocalBranchesCopy() {
    return myState.myContainedLocalBranches == null ? Collections.<String>emptySet() : new HashSet<String>(myState.myContainedLocalBranches);
  }

  public Set<String> getRemoteBranchesCopy() {
    return myState.myContainedRemoteBranches == null ? Collections.<String>emptySet() :  new HashSet<String>(myState.myContainedRemoteBranches);
  }

  public void iterateBranches(final PairConsumer<String, Boolean> consumer) {
    for (String item : myState.myContainedLocalBranches) {
      consumer.consume(item, true);
    }
    for (String item : myState.myContainedRemoteBranches) {
      consumer.consume(item, false);
    }
  }

  public void setLocal(final List<String> local) {
    Collections.sort(local);
    myState.myContainedLocalBranches = local;
  }

  public void setRemote(final List<String> branches) {
    Collections.sort(branches);
    myState.myContainedRemoteBranches = branches;
  }
}

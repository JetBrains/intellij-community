/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 * @author irengrig
 *         Date: 7/1/11
 *         Time: 1:11 PM
 */
@State(
  name = "GitLogSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )}
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
    public Set<String> myActiveRoots = new HashSet<String>();

    public String mySelectedBranch = null;
    public List<String> myStructureFilterPaths = new ArrayList<String>();
    public String mySelectedUser = null;
    public boolean mySelectedUserIsMe;
    // false => filter
    public boolean myHighlight = true;
    public boolean myShowTree = true;
    public boolean myTopoOrder = false;
    public boolean myShowDetails = true;

    public MyDateState myDateState = new MyDateState();
  }

  public static class MyDateState {
    public boolean mySelectedTime = false;
    public long myTimeBefore = -1;
    public long myTimeAfter = -1;
    public String myPresetFilter = null;
  }

  @Override
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(MyState state) {
    myState = state;
  }

  // edit right here..
  public MyDateState getDateState() {
    return myState.myDateState;
  }

  public boolean isTopoOrder() {
    return myState.myTopoOrder;
  }

  public void setTopoOrder(final boolean value) {
    myState.myTopoOrder = value;
  }
  
  public Set<String> getActiveRoots() {
    return myState.myActiveRoots;
  }
  
  public void setActiveRoots(final Set<String> set) {
    myState.myActiveRoots.clear();
    myState.myActiveRoots.addAll(set);
  }

  public boolean isShowTree() {
    return myState.myShowTree;
  }

  public void setShowTree(final boolean value) {
    myState.myShowTree = value;
  }
  
  public void setSelectedUser(final String selected) {
    myState.mySelectedUser = selected;
  }
  
  public void setSelectedUserIsMe(final boolean value) {
    myState.mySelectedUserIsMe = value;
  }

  public boolean isSelectedUserMe() {
    return myState.mySelectedUserIsMe;
  }

  public void setSelectedBranch(final String branch) {
    myState.mySelectedBranch = branch;
  }

  public void setSelectedPaths(final Collection<VirtualFile> paths) {
    if (paths == null) {
      myState.myStructureFilterPaths = null;
      return;
    }
    myState.myStructureFilterPaths = new ArrayList<String>();
    for (VirtualFile path : paths) {
      myState.myStructureFilterPaths.add(path.getPath());
    }
  }
  
  public String getSelectedBranch() {
    return myState.mySelectedBranch;
  }
  
  public String getSelectedUser() {
    return myState.mySelectedUser;
  }
  
  public List<String> getSelectedPaths() {
    return myState.myStructureFilterPaths;
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

  public boolean isHighlight() {
    return myState.myHighlight;
  }

  public void setHighlight(final boolean value) {
    myState.myHighlight = value;
  }

  public boolean isShowDetails() {
    return myState.myShowDetails;
  }

  public void setShowDetails(boolean showDetails) {
    myState.myShowDetails = showDetails;
  }
}

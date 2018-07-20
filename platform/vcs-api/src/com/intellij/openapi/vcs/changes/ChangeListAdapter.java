/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import java.util.Collection;

/**
 * @author yole
 */
public class ChangeListAdapter implements ChangeListListener {
  public void changeListsChanged() {
  }


  @Override
  public void changeListAdded(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void changeListRemoved(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void changeListRenamed(ChangeList list, String oldName) {
    changeListsChanged();
  }

  @Override
  public void changeListCommentChanged(ChangeList list, String oldComment) {
    changeListsChanged();
  }

  @Override
  public void changeListChanged(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
    changeListsChanged();
  }


  @Override
  public void changesAdded(Collection<Change> changes, ChangeList toList) {
    changeListsChanged();
  }

  @Override
  public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
    changeListsChanged();
  }

  @Override
  public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
    changeListsChanged();
  }

  @Override
  public void allChangeListsMappingsChanged() {
    changeListsChanged();
  }
}
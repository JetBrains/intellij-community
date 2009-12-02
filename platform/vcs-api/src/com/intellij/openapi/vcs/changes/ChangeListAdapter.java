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
  public void changeListAdded(ChangeList list) {
  }

  public void changeListRemoved(ChangeList list) {
  }

  public void changeListChanged(ChangeList list) {
  }

  public void changeListRenamed(ChangeList list, String oldName) {
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
  }

  public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
  }

  public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
  }

  public void changesAdded(Collection<Change> changes, ChangeList toList) {
  }
}
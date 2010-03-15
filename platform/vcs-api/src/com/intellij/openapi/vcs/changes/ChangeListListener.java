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

import java.util.EventListener;
import java.util.Collection;

/**
 * @author max
 *
 * @see com.intellij.openapi.vcs.changes.ChangeListManager#addChangeListListener(ChangeListListener)
 * @see com.intellij.openapi.vcs.changes.ChangeListManager#removeChangeListListener(ChangeListListener)  
 */
public interface ChangeListListener extends EventListener {
  void changeListAdded(ChangeList list);
  void changesRemoved(Collection<Change> changes, ChangeList fromList);
  void changesAdded(Collection<Change> changes, ChangeList toList);
  void changeListRemoved(ChangeList list);
  void changeListChanged(ChangeList list);
  void changeListRenamed(ChangeList list, String oldName);
  void changeListCommentChanged(ChangeList list, String oldComment);
  void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList);
  void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList);
  void unchangedFileStatusChanged();
  void changeListUpdateDone();
}

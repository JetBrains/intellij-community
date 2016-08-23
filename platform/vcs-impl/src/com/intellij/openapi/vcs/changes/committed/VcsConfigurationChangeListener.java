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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;

import java.util.List;

public interface VcsConfigurationChangeListener {
  Topic<Notification> BRANCHES_CHANGED = new Topic<>("branch mapping changed", Notification.class);
  Topic<DetailedNotification> BRANCHES_CHANGED_RESPONSE = new Topic<>("branch mapping changed (detailed)", DetailedNotification.class);

  interface Notification {
    void execute(final Project project, final VirtualFile vcsRoot);
  }

  interface DetailedNotification {
    void execute(final Project project, final VirtualFile vcsRoot, final List<CommittedChangeList> cachedList);
  }
}

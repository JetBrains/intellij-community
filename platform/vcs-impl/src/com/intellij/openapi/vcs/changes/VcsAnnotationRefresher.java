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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/19/12
 * Time: 4:30 PM
 */
public interface VcsAnnotationRefresher {
  Topic<VcsAnnotationRefresher> LOCAL_CHANGES_CHANGED = Topic.create("LOCAL_CHANGES_CHANGED", VcsAnnotationRefresher.class);

  void dirtyUnder(VirtualFile file);
  void dirty(BaseRevision currentRevision);
  void dirty(String path);
  void configurationChanged(final VcsKey key);
}

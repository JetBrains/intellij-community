/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @see ChangeListManagerImpl#notifyUnchangedFileStatusChanged
 */
public interface VcsManagedFilesHolder extends FilePathHolder {
  ProjectExtensionPointName<Provider> VCS_UNVERSIONED_FILES_HOLDER_EP = new ProjectExtensionPointName<>("com.intellij.vcs.unversionedFilesHolder");
  ProjectExtensionPointName<Provider> VCS_IGNORED_FILES_HOLDER_EP = new ProjectExtensionPointName<>("com.intellij.vcs.ignoredFilesHolder");

  @Topic.ProjectLevel
  Topic<VcsManagedFilesHolderListener> TOPIC = Topic.create("VcsManagedFilesHolder update", VcsManagedFilesHolderListener.class);

  default boolean isInUpdatingMode() {return false;}

  interface Provider {
    @NotNull
    AbstractVcs getVcs();

    @NotNull
    VcsManagedFilesHolder createHolder();
  }

  interface VcsManagedFilesHolderListener extends EventListener {
    @CalledInAny
    void updatingModeChanged();
  }
}

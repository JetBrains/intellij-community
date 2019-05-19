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

package com.intellij.openapi.vcs;

import java.util.EventListener;

/**
 * <p>Allows to receive notifications about changes in VCS configuration for the project.</p>
 * <p>Use the {@link ProjectLevelVcsManager#VCS_CONFIGURATION_CHANGED} MessageBus topic to subscribe.</p>
 */
public interface VcsListener extends EventListener {
  /**
   * Notifies that the per-directory VCS mapping has changed.
   */
  void directoryMappingChanged();
}
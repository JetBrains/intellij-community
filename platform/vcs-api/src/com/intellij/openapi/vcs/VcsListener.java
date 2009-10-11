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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.07.2006
 * Time: 14:48:30
 */
package com.intellij.openapi.vcs;

import java.util.EventListener;

/**
 * Allows to receive notifications about changes in VCS configuration for the project.
 *
 * @see ProjectLevelVcsManager#addVcsListener
 * @since 6.0
 */
public interface VcsListener extends EventListener {
  /**
   * Notifies that the per-directory VCS mapping has changed.
   */
  void directoryMappingChanged();
}
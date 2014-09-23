/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.preview;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PreviewManager {
  class SERVICE {

    private SERVICE() {
    }

    @Nullable
    public static PreviewManager getInstance(@NotNull Project project) {
      if (!UISettings.getInstance().NAVIGATE_TO_PREVIEW) return null;
      return ServiceManager.getService(project, PreviewManager.class);
    }
  }

  /**
   * @return <code>null</code> if provider is not available / not active or if it forces to use standard view instead of preview at the moment
   */
  @Nullable
  <V, C> C preview(@NotNull PreviewProviderId<V, C> id, V data, boolean requestFocus);

  <V, C> boolean moveToStandardPlace(@NotNull PreviewProviderId<V, C> id, V data);

  <V, C> void close(@NotNull PreviewProviderId<V, C> id, V data);
}

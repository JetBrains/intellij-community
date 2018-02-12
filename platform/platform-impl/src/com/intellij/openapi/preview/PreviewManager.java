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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PreviewManager {
  class SERVICE {

    private SERVICE() {
    }

    @Nullable
    private static PreviewManager getInstance(@NotNull Project project) {
      return null;//disabled for a while
      //if (!UISettings.getInstance().NAVIGATE_TO_PREVIEW) return null;
      //return ServiceManager.getService(project, PreviewManager.class);
    }

    /**
     * @return null if preview cannot be performed
     */
    @Nullable
    public static <V, C> C preview(@NotNull Project project, @NotNull PreviewProviderId<V, C> id, V data, boolean requestFocus) {
      PreviewManager instance = getInstance(project);
      if (instance == null) return null;
      return instance.preview(id, data, requestFocus);
    }

    public static <V, C> void close(@NotNull Project project, @NotNull PreviewProviderId<V, C> id, V data) {
      PreviewManager instance = getInstance(project);
      if (instance != null) {
        instance.close(id, data);
      }
    }

    public static <V, C> void moveToStandardPlaceImpl(@NotNull Project project, @NotNull PreviewProviderId<V, C> id, V data) {
      PreviewManager instance = getInstance(project);
      if (instance != null) {
        instance.moveToStandardPlaceImpl(id, data);
      }
    }
  }

  /**
   * @return {@code null} if provider is not available / not active or if it forces to use standard view instead of preview at the moment
   */
  @Nullable
  <V, C> C preview(@NotNull PreviewProviderId<V, C> id, V data, boolean requestFocus);

  <V, C> void moveToStandardPlaceImpl(@NotNull PreviewProviderId<V, C> id, V data);

  <V, C> void close(@NotNull PreviewProviderId<V, C> id, V data);
}

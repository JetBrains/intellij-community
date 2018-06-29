/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class ProjectLocator {

  public static ProjectLocator getInstance() {
    return ServiceManager.getService(ProjectLocator.class);
  }

  /**
   * Returns an open project which contains the given file.
   * This is a guess-method, so if several projects contain the file, only one will be returned.
   * Also a project may be returned though it doesn't contain the file for sure (see implementations).
   * @param file file to be located in projects.
   * @return project which probably contains the file, or null if couldn't guess (for example, there are no open projects).
   */
  @Nullable
  public abstract Project guessProjectForFile(@Nullable VirtualFile file);

  /**
  * Gets all open projects containing the given file.
  * If none does, an empty list is returned.
  * @param file file to be located in projects.
  * @return list of open projects containing this file.
  */
  @NotNull
  public abstract Collection<Project> getProjectsForFile(VirtualFile file);

  public static <T, E extends Throwable> T computeWithPreferredProject(@NotNull VirtualFile file,
                                                                       @NotNull Project preferredProject,
                                                                       @NotNull ThrowableComputable<T, E> action) throws E {
    Map<VirtualFile, Project> local = ourPreferredProjects.get();
    local.put(file, preferredProject);
    try {
      return action.compute();
    }
    finally {
      local.remove(file);
    }
  }

  @Nullable
  static Project getPreferredProject(@NotNull VirtualFile file) {
    return ourPreferredProjects.get().get(file);
  }

  private static final ThreadLocal<Map<VirtualFile, Project>> ourPreferredProjects = ThreadLocal.withInitial(() -> new HashMap<>());
}

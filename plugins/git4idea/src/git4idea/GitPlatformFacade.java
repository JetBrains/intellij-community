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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use direct instance or ServiceManager methods to access platform structures.
 */
@Deprecated
public interface GitPlatformFacade {

  /**
   * @deprecated To remove in IDEA 2017. Use {@link ChangeListManager#getInstance(Project)}.
   */
  @Deprecated
  ChangeListManagerEx getChangeListManager(@NotNull Project project);

  /**
   * @deprecated To remove in IDEA 2017. Use {@link VfsUtil#markDirtyAndRefresh(boolean, boolean, boolean, VirtualFile...)}.
   */
  @Deprecated
  void hardRefresh(@NotNull VirtualFile root);
}

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
package git4idea.staging;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitIndexFileSystem extends DeprecatedVirtualFileSystem {
  private static final String PROTOCOL = "gitIndexFs";

  public GitIndexFileSystem() {
    startEventPropagation();
  }

  public static GitIndexFileSystem getInstance() {
    return (GitIndexFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void fireBeforeContentsChange(Object requestor, @NotNull VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  @Override
  public void fireContentsChanged(Object requestor, @NotNull VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }


  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return null;
  }


  @Override
  public void refresh(boolean asynchronous) {
  }

  @NotNull
  @Override
  public String extractPresentableUrl(@NotNull String path) {
    return "Staged: " + super.extractPresentableUrl(path);
  }
}

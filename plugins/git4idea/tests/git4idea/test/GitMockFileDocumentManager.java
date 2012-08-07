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
package git4idea.test;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Kirill Likhodedov
 */
class GitMockFileDocumentManager extends FileDocumentManager {
  @Override
  public Document getDocument(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Document getCachedDocument(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getFile(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveAllDocuments() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveDocument(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveDocumentAsIs(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Document[] getUnsavedDocuments() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDocumentUnsaved(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFileModified(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reloadFromDisk(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project) {
    try {
      return FileUtil.loadFile(new File(file.getPath())).contains("\r\n") ? "\r\n" : "\n";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean requestWriting(@NotNull Document document, @Nullable Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reloadFiles(VirtualFile... files) {
    throw new UnsupportedOperationException();
  }
}

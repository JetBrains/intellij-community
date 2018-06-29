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
package com.intellij.diff;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link SimpleContent} which content is retrieved from a file which exists or existed in the project.
 */
public class FileAwareSimpleContent extends SimpleContent {

  @NotNull private final Project myProject;
  @NotNull private final FilePath myFilePath;

  public FileAwareSimpleContent(@NotNull Project project, @NotNull FilePath filePath, @NotNull String text, @Nullable FileType type) {
    super(text, type);
    myProject = project;
    myFilePath = filePath;
  }

  @Override
  public Navigatable getOpenFileDescriptor(int offset) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(myFilePath.getIOFile());
    return file == null ? null : PsiNavigationSupport.getInstance().createNavigatable(myProject, file, offset);
  }

}

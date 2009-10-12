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
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class DiffContentFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.ex.DiffContentFactory");

  private DiffContentFactory() {}

  @Nullable
  public static SimpleDiffRequest compareVirtualFiles(Project project, VirtualFile file1, VirtualFile file2, String title) {
    DiffContent content1 = DiffContent.fromFile(project, file1);
    DiffContent content2 = DiffContent.fromFile(project, file2);
    if (content1 == null || content2 == null) return null;
    SimpleDiffRequest diffRequest = new SimpleDiffRequest(project, title);
    diffRequest.setContents(content1, content2);
    return diffRequest;
  }
}
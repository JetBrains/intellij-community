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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PatchBaseDirectoryDetector {
  public static PatchBaseDirectoryDetector getInstance(Project project) {
    return ServiceManager.getService(project, PatchBaseDirectoryDetector.class);
  }

  @Nullable
  public abstract Result detectBaseDirectory(String name);

  public abstract Collection<VirtualFile> findFiles(String fileName);

  public static class Result {
    public String baseDir;
    public int stripDirs;

    public Result(final String baseDir, final int stripDirs) {
      this.baseDir = baseDir;
      this.stripDirs = stripDirs;
    }
  }
}

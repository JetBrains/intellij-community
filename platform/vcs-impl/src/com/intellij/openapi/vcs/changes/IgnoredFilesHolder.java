/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * @author irengrig
 *         Date: 2/10/11
 *         Time: 4:54 PM
 */
public interface IgnoredFilesHolder extends FileHolder {
  void addFile(VirtualFile file);

  boolean containsFile(VirtualFile file);

  Collection<VirtualFile> values();

  void cleanAndAdjustScope(VcsModifiableDirtyScope scope);
}

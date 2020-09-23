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

package com.intellij.lang.ant.config;

import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;

public class AntNoFileException extends Exception {
  private final VirtualFile myFile;

  public AntNoFileException(final @Nls String message, final VirtualFile file) {
    super(AntBundle.message("cant.add.file.error.message", file.getPresentableUrl(), message));
    myFile = file;
  }

  public VirtualFile getFile() {
    return myFile;
  }
}

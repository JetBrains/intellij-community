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
package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.IntFunction;

/**
 * Register implementation of this extension point to draw additional text fragments after end of a line in a file editor. If you need to do
 * this in a particular {@link Editor} instance use {@link com.intellij.openapi.editor.ex.EditorEx#registerLineExtensionPainter(IntFunction)} instead.
 *
 * @author Konstantin Bulenkov
 */
public abstract class EditorLinePainter {
  public static final ExtensionPointName<EditorLinePainter> EP_NAME = ExtensionPointName.create("com.intellij.editor.linePainter");

  @Nullable 
  public abstract Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file, int lineNumber);
}

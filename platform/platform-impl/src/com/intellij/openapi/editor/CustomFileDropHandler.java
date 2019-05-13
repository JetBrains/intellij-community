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
package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public abstract class CustomFileDropHandler {
  public static final ExtensionPointName<CustomFileDropHandler> CUSTOM_DROP_HANDLER_EP =
    ExtensionPointName.create("com.intellij.customFileDropHandler");

  public abstract boolean canHandle(@NotNull Transferable t, @Nullable Editor editor);

  public abstract boolean handleDrop(@NotNull Transferable t, @Nullable Editor editor, final Project project);
}

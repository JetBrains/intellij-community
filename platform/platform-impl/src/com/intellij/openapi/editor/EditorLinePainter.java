// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.IntFunction;

/**
 * An implementation of this extension point can draw additional text fragments
 * after the end of a line in a file editor.
 * <p>
 * If you need to do this in a particular {@link Editor} instance,
 * use {@link com.intellij.openapi.editor.ex.EditorEx#registerLineExtensionPainter(IntFunction)} instead.
 *
 * @author Konstantin Bulenkov
 */
public abstract class EditorLinePainter {
  public static final ExtensionPointName<EditorLinePainter> EP_NAME = ExtensionPointName.create("com.intellij.editor.linePainter");

  public abstract @Nullable Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project,
                                                                            @NotNull VirtualFile file,
                                                                            int lineNumber);
}

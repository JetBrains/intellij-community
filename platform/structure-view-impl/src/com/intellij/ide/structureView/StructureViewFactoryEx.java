// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.structureView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Belyaev
 */
public abstract class StructureViewFactoryEx extends StructureViewFactory {

  public abstract @Nullable StructureViewWrapper getStructureViewWrapper();

  public abstract @NotNull Collection<StructureViewExtension> getAllExtensions(@NotNull Class<? extends PsiElement> type);

  public abstract void setActiveAction(final String name, final boolean state);

  public abstract boolean isActionActive(final String name);

  public static StructureViewFactoryEx getInstanceEx(final Project project) {
    return (StructureViewFactoryEx)getInstance(project);
  }

  public abstract void runWhenInitialized(@NotNull Runnable runnable);
}

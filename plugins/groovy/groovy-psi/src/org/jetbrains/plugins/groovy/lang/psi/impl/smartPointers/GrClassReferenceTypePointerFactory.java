// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.ClassTypePointerFactory;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.SmartTypePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;

public final class GrClassReferenceTypePointerFactory implements ClassTypePointerFactory {
  @Override
  public @Nullable SmartTypePointer createClassTypePointer(@NotNull PsiClassType classType, @NotNull Project project) {
    if (classType instanceof GrClassReferenceType) {
      return new GrClassReferenceTypePointer(((GrClassReferenceType)classType), project);
    }

    return null;
  }
}

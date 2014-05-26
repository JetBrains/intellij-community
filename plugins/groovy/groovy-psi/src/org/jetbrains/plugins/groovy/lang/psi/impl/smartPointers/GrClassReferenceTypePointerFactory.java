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
package org.jetbrains.plugins.groovy.lang.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.ClassTypePointerFactory;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.SmartTypePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;

/**
 * Created by Max Medvedev on 10/25/13
 */
public class GrClassReferenceTypePointerFactory implements ClassTypePointerFactory {
  @Nullable
  @Override
  public SmartTypePointer createClassTypePointer(@NotNull PsiClassType classType, @NotNull Project project) {
    if (classType instanceof GrClassReferenceType) {
      return new GrClassReferenceTypePointer(((GrClassReferenceType)classType), project);
    }

    return null;
  }
}

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

package org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author ilyas
 */
public interface GrImportStatement extends GrTopStatement {
  GrImportStatement[] EMPTY_ARRAY = new GrImportStatement[0];

  ArrayFactory<GrImportStatement> ARRAY_FACTORY = new ArrayFactory<GrImportStatement>() {
    @NotNull
    @Override
    public GrImportStatement[] create(int count) {
      return new GrImportStatement[count];
    }
  };

  @Nullable
  GrCodeReferenceElement getImportReference();

  @Nullable
  String getImportedName();

  boolean isOnDemand();

  boolean isStatic();

  boolean isAliasedImport();

  @NotNull
  GrModifierList getAnnotationList();

  @Nullable
  PsiClass resolveTargetClass();

  @Nullable
  PsiElement getAliasNameElement();
}

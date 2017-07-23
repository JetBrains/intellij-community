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
package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author ilyas
 */
public interface GrReferenceList extends GroovyPsiElement, PsiReferenceList {
  GrReferenceList[] EMPTY_ARRAY = new GrReferenceList[0];
  ArrayFactory<GrReferenceList> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrReferenceList[count];

  @Nullable
  PsiElement getKeyword();

  @NotNull
  GrCodeReferenceElement[] getReferenceElementsGroovy();

}

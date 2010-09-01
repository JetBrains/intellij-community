/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(PsiElement element) {
    return super.canProcessElement(element) && element instanceof GroovyPsiElement;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element), element);
  }
}

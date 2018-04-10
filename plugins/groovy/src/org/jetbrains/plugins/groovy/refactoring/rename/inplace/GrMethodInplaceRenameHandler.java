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
package org.jetbrains.plugins.groovy.refactoring.rename.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GrMethodInplaceRenameHandler extends MemberInplaceRenameHandler {
  @Override
  protected boolean isAvailable(@Nullable PsiElement element, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!editor.getSettings().isVariableInplaceRenameEnabled()) return false;
    return element instanceof GrMethod && file instanceof GroovyFile;
  }

  @NotNull
  @Override
  protected MemberInplaceRenamer createMemberRenamer(@NotNull PsiElement element,
                                                     @NotNull PsiNameIdentifierOwner elementToRename,
                                                     @NotNull Editor editor) {
    return new GrMethodInplaceRenamer(elementToRename, element, editor);
  }
}

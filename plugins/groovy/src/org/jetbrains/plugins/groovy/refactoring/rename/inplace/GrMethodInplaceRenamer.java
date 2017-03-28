/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * Created by Max Medvedev on 26/03/14
 */
public class GrMethodInplaceRenamer extends MemberInplaceRenamer {
  public GrMethodInplaceRenamer(PsiNamedElement elementToRename, PsiElement substituted, Editor editor) {
    super(elementToRename, substituted, editor);
  }

  @Override
  protected boolean isIdentifier(String newName, Language language) {
    return true;
  }

  @Nullable
  @Override
  protected PsiElement getNameIdentifier(PsiElement elementToRename) {
    return elementToRename instanceof GrNamedElement ? ((GrNamedElement)elementToRename).getNameIdentifierGroovy() : null;
  }

  @NotNull
  @Override
  protected TextRange getRangeToRename(@NotNull PsiElement element) {
    TextRange stringContentRange = GrStringUtil.getStringContentRange(element);
    if (stringContentRange != null) return stringContentRange;
    return super.getRangeToRename(element);
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.references.IdeaModuleReference;
import org.jetbrains.idea.devkit.references.ModuleLibraryReference;
import org.jetbrains.idea.devkit.references.ProjectLibraryReference;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

/**
 * @author nik
 */
public class RuntimeModuleReferenceConverter implements CustomReferenceConverter<String> {
  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    String id = value.getValue();
    if (id == null) return PsiReference.EMPTY_ARRAY;

    if (id.startsWith(RuntimeModuleId.LIB_NAME_PREFIX)) {
      String libraryName = StringUtil.trimStart(id, RuntimeModuleId.LIB_NAME_PREFIX);
      int dot = libraryName.indexOf('.');
      int prefixLen = RuntimeModuleId.LIB_NAME_PREFIX.length();
      if (dot > 0 && dot < libraryName.length() && (!Character.isDigit(libraryName.charAt(dot-1)) || !Character.isDigit(libraryName.charAt(dot+1)))) {
        IdeaModuleReference moduleReference = new IdeaModuleReference(element, false);
        moduleReference.setRangeInElement(moduleReference.getRangeInElement().cutOut(TextRange.from(prefixLen, dot)));
        ModuleLibraryReference moduleLibraryReference = new ModuleLibraryReference(element, moduleReference);
        moduleLibraryReference.setRangeInElement(moduleLibraryReference.getRangeInElement().shiftRight(prefixLen + dot + 1).grown(-prefixLen - dot - 1));
        return new PsiReference[]{moduleReference, moduleLibraryReference};
      }
      ProjectLibraryReference libraryReference = new ProjectLibraryReference(element, true);
      libraryReference.setRangeInElement(libraryReference.getRangeInElement().shiftRight(prefixLen).grown(-prefixLen));
      return new PsiReference[]{libraryReference};
    }
    return new PsiReference[]{new IdeaModuleReference(element, true)};
  }
}

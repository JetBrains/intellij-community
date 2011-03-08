/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class GroovyReferenceCharFilter extends CharFilter {
  @Nullable
  public Result acceptChar(char c, int pefixLength, Lookup lookup) {
    final PsiFile psiFile = lookup.getPsiFile();
    if (psiFile != null && !psiFile.getViewProvider().getLanguages().contains(GroovyFileType.GROOVY_LANGUAGE)) return null;

    LookupElement item = lookup.getCurrentItem();
    if (item == null) return null;

    if (Character.isJavaIdentifierPart(c) || c == '\'') {
      return Result.ADD_TO_PREFIX;
    }

    if (c == '[') return CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '<' && item.getObject() instanceof PsiClass) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

    return null;
  }

}

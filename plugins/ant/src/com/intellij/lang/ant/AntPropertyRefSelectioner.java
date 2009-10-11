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
package com.intellij.lang.ant;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 14, 2007
 */
public class AntPropertyRefSelectioner implements ExtendWordSelectionHandler {
  public boolean canSelect(final PsiElement e) {
    return getRangesToSelect(e).size() > 0;
  }

  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    List<TextRange> rangesToSelect = getRangesToSelect(e);
    for (Iterator it = rangesToSelect.iterator(); it.hasNext();) {
      final TextRange range = (TextRange)it.next();
      if (!range.contains(cursorOffset)) {
        it.remove();
      }
    }
    return rangesToSelect;
  }
  
  @NotNull
  private static List<TextRange> getRangesToSelect(PsiElement e) {
    final PsiFile containingFile = e.getContainingFile();
    if (containingFile == null) {
      return Collections.emptyList();
    }
    final AntFile antFile = AntSupport.getAntFile(containingFile);
    if (antFile == null) {
      return Collections.emptyList();
    }
    final PsiElement antElement = antFile.findElementAt(e.getTextOffset());
    if (antElement == null) {
      return Collections.emptyList();
    }
    final TextRange antElementRange = antElement.getTextRange();
    final TextRange selectionElementRange = e.getTextRange();
    final PsiReference[] refs = antElement.getReferences();
    ArrayList<TextRange> ranges = new ArrayList<TextRange>(refs.length);
    for (PsiReference ref : refs) {
      if (ref instanceof AntPropertyReference) {
        TextRange refRange = ref.getRangeInElement();
        refRange = refRange.shiftRight(antElementRange.getStartOffset());
        if (selectionElementRange.contains(refRange)) {
          ranges.add(refRange);
        }
      }
    }
    return ranges;
  }
}

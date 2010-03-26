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
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.codeStyle.ReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyReferenceAdjuster extends ReferenceAdjuster {
  public static final ReferenceAdjuster INSTANCE = new GroovyReferenceAdjuster(true, false);

  public GroovyReferenceAdjuster(boolean useFqInJavadoc, boolean useFqInCode) {
    super(useFqInJavadoc, useFqInCode);
  }

  public void shortenReferences(PsiElement element, int startInElement, int endInElement) {
    List<GrCodeReferenceElement> refs = new ArrayList<GrCodeReferenceElement>();
    collectReferences(element, 0, startInElement, endInElement, refs);
    for (GrCodeReferenceElement referenceElement : refs) {
      PsiUtil.shortenReference(referenceElement);
    }
  }

  private static int collectReferences(PsiElement element, int currOffset, int startInElement, int endInElement, List<GrCodeReferenceElement> result) {
    if (element instanceof GrCodeReferenceElement && startInElement <= currOffset && currOffset <= endInElement) {
      result.add((GrCodeReferenceElement) element);
    }

    PsiElement first = element.getFirstChild();
    if (first != null) {
      for (PsiElement run = first; run != null; run = run.getNextSibling()) {
        currOffset = collectReferences(run, currOffset, startInElement, endInElement, result);
        if (currOffset > endInElement) break;
      }
      return currOffset;
    }

    return currOffset += element.getTextLength();
  }
}

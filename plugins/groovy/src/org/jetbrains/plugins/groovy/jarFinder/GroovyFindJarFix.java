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
package org.jetbrains.plugins.groovy.jarFinder;

import com.intellij.jarFinder.FindJarFix;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class GroovyFindJarFix extends FindJarFix<GrReferenceElement> {
  public GroovyFindJarFix(GrReferenceElement ref) {
    super(ref);
  }

  @Override
  protected Collection<String> getFqns(@NotNull GrReferenceElement ref) {
    GrImportStatement importStatement = PsiTreeUtil.getParentOfType(ref.getElement(), GrImportStatement.class);

    //from static imports
    if (importStatement != null) {
      GrCodeReferenceElement reference = importStatement.getImportReference();
      if (reference != null) {
        return Collections.singleton(reference.getText());
      }

      return Collections.emptyList();
    }

    if (ref.getQualifier() != null) return Collections.emptyList();

    final String className = ref.getReferenceName();
    if (className == null) return Collections.emptyList();

    PsiFile file = ref.getContainingFile().getOriginalFile();
    if (!(file instanceof GroovyFile)) return Collections.emptyList();

    GrImportStatement[] importList = ((GroovyFile)file).getImportStatements();

    for (GrImportStatement imp : importList) {
      if (className.equals(imp.getImportedName())) {
        GrCodeReferenceElement importReference = imp.getImportReference();
        if (importReference == null) return Collections.emptyList();
        return Collections.singleton(importReference.getText());
      }
    }

    return Collections.emptyList();
  }
}

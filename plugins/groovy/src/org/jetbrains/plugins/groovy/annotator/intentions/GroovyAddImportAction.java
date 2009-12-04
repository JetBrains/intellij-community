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

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author peter
 */
public class GroovyAddImportAction extends ImportClassFixBase<GrReferenceElement> {
  public GroovyAddImportAction(GrReferenceElement ref) {
    super(ref);
  }

  @Override
  protected void bindReference(GrReferenceElement ref, PsiClass targetClass) {
    ref.bindToElement(targetClass);
  }

  @Override
  protected String getReferenceName(GrReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected boolean hasTypeParameters(GrReferenceElement reference) {
    return reference.getTypeArguments().length > 0;
  }

  @Override
  protected String getQualifiedName(GrReferenceElement reference) {
    return reference.getCanonicalText();
  }

  @Override
  protected boolean isQualified(GrReferenceElement reference) {
    return reference.getQualifier() != null;
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(PsiFile psiFile, String name) {
    if (!(psiFile instanceof GroovyFile)) return false;
    final GrImportStatement[] importStatements = ((GroovyFile)psiFile).getImportStatements();
    for (GrImportStatement importStatement : importStatements) {
      final GrCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null || importReference.resolve() != null) {
        continue;
      }
      if (importStatement.isOnDemand() || Comparing.strEqual(importStatement.getImportedName(), name)) {
        return true;
      }
    }
    return false;
  }

  protected boolean isAccessible(PsiClass aClass, GrReferenceElement reference) {
    return true;
  }
}

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

/*
 * @author max
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeCopyHandler;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

/**
 * @author peter
 */
public class GroovyChangeUtilSupport implements TreeCopyHandler {

  @Override
  public TreeElement decodeInformation(TreeElement element, final Map<Object, Object> decodingState) {
    if (element instanceof CompositeElement) {
      if (element.getElementType() == GroovyElementTypes.REFERENCE_ELEMENT || element.getElementType() == GroovyElementTypes.REFERENCE_EXPRESSION) {
        GrReferenceElement ref = (GrReferenceElement)SourceTreeToPsiMap.treeElementToPsi(element);
        final PsiMember refMember = element.getCopyableUserData(REFERENCED_MEMBER_KEY);
        if (refMember != null) {
          element.putCopyableUserData(REFERENCED_MEMBER_KEY, null);
          PsiElement refElement1 = ref.resolve();
          if (!refMember.getManager().areElementsEquivalent(refMember, refElement1)) {
            try {
              if (!(refMember instanceof PsiClass) || ref.getQualifier() == null) {
                // can restore only if short (otherwise qualifier should be already restored)
                ref = (GrReferenceElement)ref.bindToElement(refMember);
              }
            }
            catch (IncorrectOperationException ignored) {
            }
            return (TreeElement)SourceTreeToPsiMap.psiElementToTree(ref);
          }
        }
        return element;
      }
    }
    return null;
  }

  @Override
  public void encodeInformation(final TreeElement element, final ASTNode original, final Map<Object, Object> encodingState) {
    if (original instanceof CompositeElement) {
      if (original.getElementType() == GroovyElementTypes.REFERENCE_ELEMENT ||
          original.getElementType() == GroovyElementTypes.REFERENCE_EXPRESSION) {
        PsiElement psi = original.getPsi();
        if (!PsiUtil.isThisOrSuperRef(psi) && psi.getProject().isInitialized()) {
          final GroovyResolveResult result = ((GrReferenceElement)psi).advancedResolve();
          if (result != null) {
            final PsiElement target = result.getElement();

            if (target instanceof PsiClass ||
                (target instanceof PsiMethod || target instanceof PsiField) &&
                ((PsiMember)target).hasModifierProperty(PsiModifier.STATIC) &&
                result.getCurrentFileResolveContext() instanceof GrImportStatement) {
              element.putCopyableUserData(REFERENCED_MEMBER_KEY, (PsiMember)target);
            }
          }
        }
      }
    }
  }

  private static final Key<PsiMember> REFERENCED_MEMBER_KEY = Key.create("REFERENCED_MEMBER_KEY");
}

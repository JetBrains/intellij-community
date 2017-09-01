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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocFieldReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

/**
 * @author ilyas
 */
public class GrDocFieldReferenceImpl extends GrDocMemberReferenceImpl implements GrDocFieldReference {

  public GrDocFieldReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "GrDocFieldReference";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocFieldReference(this);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtil.isSimplePropertyAccessor(method)) {
          final String newPropertyName = PropertyUtil.getPropertyName(newElementName);
          if (newPropertyName != null) {
            return super.handleElementRename(newPropertyName);
          }
        }
      }
    } else if (resolved instanceof GrField && ((GrField) resolved).isProperty()) {
      final GrField field = (GrField) resolved;
      final String oldName = getReferenceName();
      if (oldName != null && oldName.equals(field.getName())) {
        if (oldName.startsWith("get")) {
          return super.handleElementRename("get" + StringUtil.capitalize(newElementName));
        } else if (oldName.startsWith("set")) {
          return super.handleElementRename("set" + StringUtil.capitalize(newElementName));
        }
      }
    }

    return super.handleElementRename(newElementName);
  }

  @Override
  protected ResolveResult[] multiResolveImpl() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = PsiUtil.getContextClass(GrDocCommentUtil.findDocOwner(this));
    }
    if (resolved instanceof PsiClass) {
      PropertyResolverProcessor processor = new PropertyResolverProcessor(name, this);
      resolved.processDeclarations(processor, ResolveState.initial(), resolved, this);
      GroovyResolveResult[] candidates = processor.getCandidates();
      if (candidates.length == 0) {
        PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass) resolved, PsiSubstitutor.EMPTY);
        MethodResolverProcessor methodProcessor = new MethodResolverProcessor(name, this, false, thisType, null, PsiType.EMPTY_ARRAY);
        MethodResolverProcessor constructorProcessor = new MethodResolverProcessor(name, this, true, thisType, null, PsiType.EMPTY_ARRAY);
        resolved.processDeclarations(methodProcessor, ResolveState.initial(), resolved, this);
        resolved.processDeclarations(constructorProcessor, ResolveState.initial(), resolved, this);
        candidates = ArrayUtil.mergeArrays(methodProcessor.getCandidates(), constructorProcessor.getCandidates());
        if (candidates.length > 0) {
          candidates = new GroovyResolveResult[]{candidates[0]};
        }
      }
      return candidates;
    }
    return ResolveResult.EMPTY_ARRAY;
  }

}

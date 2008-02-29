/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocFieldReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
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

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocFieldReference(this);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    //todo implement me!
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    //todo implement me!
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    //todo implement me!
    return false;
  }

  protected ResolveResult[] multiResolveImpl() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement.resolve();
    } else {
      resolved = getEnclosingClassOrFile(this);
    }
    if (resolved != null) {
      PropertyResolverProcessor processor = new PropertyResolverProcessor(name, this, false);
      resolved.processDeclarations(processor, PsiSubstitutor.EMPTY, resolved, this);
      return processor.getCandidates();
    }
    return new ResolveResult[0];
  }

}

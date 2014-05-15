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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author ilyas
 */
public class GrDocMethodParameterImpl extends GroovyDocPsiElementImpl implements GrDocMethodParameter {

  public GrDocMethodParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "GrDocMethodParameter";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocMethodParameter(this);
  }

  @Override
  @NotNull
  public GrDocReferenceElement getTypeElement(){
    GrDocReferenceElement child = findChildByClass(GrDocReferenceElement.class);
    assert child != null;
    return child;
  }

  @Override
  @Nullable
  public GrDocTagValueToken getParameterElement(){
    return findChildByClass(GrDocTagValueToken.class);
  }
}

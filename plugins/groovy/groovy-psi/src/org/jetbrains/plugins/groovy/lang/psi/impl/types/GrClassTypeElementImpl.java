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
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrClassTypeElementImpl extends GroovyPsiElementImpl implements GrClassTypeElement {
  public GrClassTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClassTypeElement(this);
  }

  public String toString() {
    return "Type element";
  }

  @Override
  @NotNull
  public GrCodeReferenceElement getReferenceElement() {
    return (GrCodeReferenceElement)findNotNullChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @NotNull
  public PsiType getType() {
    return new GrClassReferenceType(getReferenceElement());
  }
}

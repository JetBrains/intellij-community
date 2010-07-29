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

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging;

import com.intellij.lang.ASTNode;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrPackageDefinitionImpl extends GroovyPsiElementImpl implements GrPackageDefinition {

  public GrPackageDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitPackageDefinition(this);
  }

  public String toString() {
    return "Package definition";
  }

  public String getPackageName() {
    GrCodeReferenceElement ref = getPackageReference();
    if (ref == null) return "";
    return PsiUtil.getQualifiedReferenceText(ref);
  }

  public GrCodeReferenceElement getPackageReference() {
    return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  public GrModifierList getAnnotationList() {
    return (GrModifierList)findChildByType(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  @Override
  public boolean hasModifierProperty(@Modifier @NonNls @NotNull String name) {
    final PsiModifierList list = getModifierList();
    return list != null && list.hasExplicitModifier(name);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrPackageDefinitionImpl extends GrStubElementBase<GrPackageDefinitionStub> implements GrPackageDefinition, StubBasedPsiElement<GrPackageDefinitionStub> {

  public GrPackageDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrPackageDefinitionImpl(@NotNull GrPackageDefinitionStub stub) {
    super(stub, GroovyElementTypes.PACKAGE_DEFINITION);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitPackageDefinition(this);
  }

  public String toString() {
    return "Package definition";
  }

  @Override
  public String getPackageName() {
    final GrPackageDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getPackageName();
    }

    GrCodeReferenceElement ref = getPackageReference();
    if (ref == null) return "";
    return PsiUtil.getQualifiedReferenceText(ref);
  }

  @Override
  public GrCodeReferenceElement getPackageReference() {
    return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @NotNull
  public GrModifierList getAnnotationList() {
    return getStubOrPsiChild(GroovyElementTypes.MODIFIERS);
  }

  @Override
  public PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    final PsiModifierList list = getModifierList();
    return list != null && list.hasExplicitModifier(name);
  }
}

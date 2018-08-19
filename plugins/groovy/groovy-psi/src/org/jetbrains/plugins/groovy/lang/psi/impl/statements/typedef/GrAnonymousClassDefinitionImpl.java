// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubElementType;

/**
 * @author Maxim.Medvedev
 */
public class GrAnonymousClassDefinitionImpl extends GrTypeDefinitionImpl implements GrAnonymousClassDefinition {

  private SoftReference<PsiClassType> myCachedBaseType;

  public GrAnonymousClassDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnonymousClassDefinitionImpl(GrTypeDefinitionStub stub) {
    this(stub, GroovyElementTypes.ANONYMOUS_CLASS_DEFINITION);
  }

  public GrAnonymousClassDefinitionImpl(GrTypeDefinitionStub stub, final GrStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  @NotNull
  public GrCodeReferenceElement getBaseClassReferenceGroovy() {
    GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      GrCodeReferenceElement reference = stub.getBaseClassReference();
      assert reference != null;
      return reference;
    }

    return findNotNullChildByClass(GrCodeReferenceElement.class); //not null because of definition =)
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return PsiModifier.FINAL.equals(name);
  }

  @Override
  @Nullable
  public GrArgumentList getArgumentListGroovy() {
    //noinspection ConstantConditions
    return findChildByClass(GrArgumentList.class); //not null because of definition
  }

  @Override
  public boolean isInQualifiedNew() {
    return false;
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    return JavaPsiFacade.getElementFactory(getProject()).createReferenceElementByType(getBaseClassType());
  }

  @Override
  @NotNull
  public PsiClassType getBaseClassType() {
    PsiClassType type = SoftReference.dereference(myCachedBaseType);
    if (type != null && type.isValid()) return type;

    type = new GrClassReferenceType(getBaseClassReferenceGroovy());
    myCachedBaseType = new SoftReference<>(type);
    return type;
  }

  @Override
  @Nullable
  public PsiExpressionList getArgumentList() {
    return null;
  }

  @Nullable
  private PsiClass getBaseClass() {
    return getBaseClassType().resolve();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    //noinspection ConstantConditions
    return getBaseClassReferenceGroovy().getReferenceNameElement();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof GrArgumentList) return true;

    GrCodeReferenceElement refElement = getBaseClassReferenceGroovy();
    if (refElement == place || refElement == lastParent) return true;

    return super.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  public boolean isAnonymous() {
    return true;
  }

  @Override
  public PsiClass getSuperClass() {
    final PsiClass psiClass = getBaseClass();
    if (psiClass != null && !psiClass.isInterface()) return psiClass;
    return JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, getResolveScope());
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public GrExtendsClause getExtendsClause() {
    return null;
  }

  @Override
  public GrImplementsClause getImplementsClause() {
    return null;
  }

  @NotNull
  @Override
  public PsiClassType[] getImplementsListTypes(boolean includeSynthetic) {
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassType[] getSuperTypes(boolean includeSynthetic) {
    PsiClassType baseClassType = getBaseClassType();
    PsiClass baseClass = baseClassType.resolve();
    if (baseClass == null || !baseClass.isInterface()) {
      return new PsiClassType[]{baseClassType};
    }
    else {
      PsiClassType objectType = PsiType.getJavaLangObject(getManager(), getResolveScope());
      return new PsiClassType[]{objectType, baseClassType};
    }
  }

  @Override
  public String toString() {
    return "Anonymous class";
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  @Override
  protected Object clone() {
    final Object o = super.clone();
    ((GrAnonymousClassDefinitionImpl)o).myCachedBaseType = null;
    return o;
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnonymousClassDefinition(this);
  }
}

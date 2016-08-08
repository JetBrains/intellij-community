/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Maxim.Medvedev
 */
public class GrAnonymousClassDefinitionImpl extends GrTypeDefinitionImpl implements GrAnonymousClassDefinition {
  private SoftReference<PsiClassType> myCachedBaseType = null;

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
  public PsiElement getParent() {
    return getParentByTree();
  }

  @Override
  @NotNull
  public GrCodeReferenceElement getBaseClassReferenceGroovy() {
    GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return GroovyPsiElementFactory.getInstance(getProject()).createReferenceElementFromText(stub.getBaseClassName(), this);
    }
    //noinspection ConstantConditions
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
    return GroovyPsiManager.getInstance(getProject()).findClassWithCache(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT, getResolveScope());
  }

  private PsiClassType createTypeByName(String className) {
    return TypesUtil.createTypeByFQClassName(className, this);
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    final PsiClass baseClass = getBaseClass();

    if (baseClass != null) {
      if (baseClass.isInterface()) {
        return new PsiClassType[]{createTypeByName(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT)};
      }
      else {
        if (baseClass instanceof GrTypeDefinition) {
          return new PsiClassType[]{getBaseClassType()};
        }
        else {
          return new PsiClassType[]{getBaseClassType(), createTypeByName(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT)};
        }
      }
    }
    return super.getExtendsListTypes(includeSynthetic);
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
    final PsiClass baseClass = getBaseClass();
    if (baseClass != null && baseClass.isInterface()) {
      return new PsiClassType[]{getBaseClassType(), createTypeByName(GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)};
    }
    return new PsiClassType[]{createTypeByName(GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)};
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
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnonymousClassDefinition(this);
  }
}

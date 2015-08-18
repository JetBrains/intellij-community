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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
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
  @NotNull
  public GrCodeReferenceElement getBaseClassReferenceGroovy() {
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
    final GrTypeDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.isAnonymousInQualifiedNew();
    }

    final PsiElement parent = getParent();
    return parent instanceof GrNewExpression && ((GrNewExpression)parent).getQualifier() != null;
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    final GrCodeReferenceElement ref = getBaseClassReferenceGroovy();
    final PsiElement element = ref.resolve();
    final Project project = getProject();
    if (element instanceof PsiClass) {
      final GrClassReferenceType type = new GrClassReferenceType(ref);
      return JavaPsiFacade.getElementFactory(project).createReferenceElementByType(type);
    }
    String qName = ref.getReferenceName(); //not null
    assert qName != null;
    return JavaPsiFacade.getElementFactory(project).createReferenceElementByFQClassName(qName, GlobalSearchScope.allScope(project));
  }

  @Override
  @NotNull
  public PsiClassType getBaseClassType() {
    if (isInQualifiedNew()) {
      return createClassType();
    }

    PsiClassType type = SoftReference.dereference(myCachedBaseType);
    if (type != null && type.isValid()) return type;

    type = createClassType();
    myCachedBaseType = new SoftReference<PsiClassType>(type);
    return type;
  }

  @Override
  @Nullable
  public PsiExpressionList getArgumentList() {
    return null;
  }

  @NotNull
  private PsiClassType createClassType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getBaseClassReference());
  }

  @Nullable
  private PsiClass getBaseClass() {
    final PsiElement element = getBaseClassReferenceGroovy().resolve();
    if (element instanceof PsiClass) return (PsiClass)element;
    return null;
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
  public String[] getSuperClassNames() {
    return new String[]{getBaseClassReferenceGroovy().getClassNameText()};
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
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
    return super.getExtendsListTypes();
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
  public PsiClassType[] getImplementsListTypes() {
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

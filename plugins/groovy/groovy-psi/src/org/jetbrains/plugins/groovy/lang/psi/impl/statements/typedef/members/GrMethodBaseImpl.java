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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrReflectedMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author ilyas
 */
public abstract class GrMethodBaseImpl extends GrStubElementBase<GrMethodStub> implements GrMethod, StubBasedPsiElement<GrMethodStub> {

  private static final Logger LOG = Logger.getInstance(GrMethodBaseImpl.class);

  protected GrMethodBaseImpl(final GrMethodStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrMethodBaseImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Override
  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return findNotNullChildByType(TokenSets.PROPERTY_NAMES);
  }

  @Override
  @Nullable
  public GrOpenBlock getBlock() {
    return findChildByClass(GrOpenBlock.class);
  }

  @Override
  public boolean hasBlock() {
    GrMethodStub stub = getStub();
    return stub != null ? stub.hasBlock() : GrMethod.super.hasBlock();
  }

  @Override
  public void setBlock(GrCodeBlock newBlock) {
    ASTNode newNode = newBlock.getNode().copyElement();
    final GrOpenBlock oldBlock = getBlock();
    if (oldBlock == null) {
      getNode().addChild(newNode);
      return;
    }
    getNode().replaceChild(oldBlock.getNode(), newNode);
  }

  @Override
  public GrParameter[] getParameters() {
    return getParameterList().getParameters();
  }

  @Override
  @Nullable
  public GrTypeElement getReturnTypeElementGroovy() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      final String typeText = stub.getTypeText();
      if (typeText != null) {
        return GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeText, this);
      }
      else {
        return null;
      }
    }

    return (GrTypeElement)findChildByType(TokenSets.TYPE_ELEMENTS);
  }

  @Override
  public PsiType getInferredReturnType() {
    if (isConstructor()) {
      return null;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //todo uncomment when EAP is on
      //LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread()); //this is a potentially long action
    }
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, ourTypesCalculator);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    if (ResolveUtil.shouldProcessClasses(classHint)) {
      for (final GrTypeParameter typeParameter : getTypeParameters()) {
        if (!ResolveUtil.processElement(processor, typeParameter, state)) return false;
      }
    }

    if (ResolveUtil.shouldProcessProperties(classHint)) {
      for (final GrParameter parameter : getParameters()) {
        if (!ResolveUtil.processElement(processor, parameter, state)) return false;
      }
    }

    return true;
  }

  @Override
  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  private static final Function<GrMethodBaseImpl, PsiType> ourTypesCalculator = new NullableFunction<GrMethodBaseImpl, PsiType>() {
    private boolean hasTypeParametersToInfer(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return false;

      final Iterable<PsiTypeParameter> iterable = com.intellij.psi.util.PsiUtil.typeParametersIterable(aClass);
      if (!iterable.iterator().hasNext()) {
        return false;
      }

      for (PsiTypeParameter parameter : iterable) {
        PsiType type = resolveResult.getSubstitutor().substitute(parameter);
        if (type != null) {
          if (!(type instanceof PsiWildcardType) || ((PsiWildcardType)type).getBound() != null) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public PsiType fun(GrMethodBaseImpl method) {
      PsiType nominal = method.getNominalType();
      if (nominal != null) {
        if (!(nominal instanceof PsiClassType && hasTypeParametersToInfer((PsiClassType)nominal))) {
          return nominal;
        }
      }

      final GrOpenBlock block = method.getBlock();
      if (block != null) {
        PsiType inferred = GroovyPsiManager.inferType(method, new MethodTypeInferencer(block));
        if (inferred != null) {
          if (nominal == null || nominal.isAssignableFrom(inferred)) {
            return inferred;
          }
        }
      }
      if (nominal != null) {
        return nominal;
      }

      return TypesUtil.getJavaLangObject(method);
    }
  };

  @Override
  @Nullable
  public PsiType getReturnType() {
    if (isConstructor()) {
      return null;
    }

    final PsiType type = getNominalType();
    if (type != null) {
      return type;
    }

    return TypesUtil.getJavaLangObject(this);
  }

  @Nullable
  private PsiType getNominalType() {
    if (PsiImplUtil.isMainMethod(this)) {
      return PsiType.VOID;
    }

    final GrTypeElement element = getReturnTypeElementGroovy();
    if (element != null) {
      return element.getType();
    }
    return null;
  }

  @Override
  @Nullable
  public GrTypeElement setReturnType(@Nullable PsiType newReturnType) {
    GrTypeElement typeElement = getReturnTypeElementGroovy();
    if (newReturnType == null || newReturnType == PsiType.NULL) {
      if (typeElement != null) typeElement.delete();
      insertPlaceHolderToModifierList();
      return null;
    }
    final GrTypeElement stub = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(newReturnType);
    GrTypeElement newTypeElement;
    if (typeElement == null) {
      final GrTypeParameterList typeParemeterList = getTypeParameterList();
      PsiElement anchor = typeParemeterList != null ? typeParemeterList : getModifierList();
      newTypeElement = (GrTypeElement)addAfter(stub, anchor);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(stub);
    }

    return newTypeElement;
  }

  private void insertPlaceHolderToModifierList() {
    final GrModifierList list = getModifierList();
    PsiImplUtil.insertPlaceHolderToModifierListAtEndIfNeeded(list);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Nullable
  @Override
  protected Icon getElementIcon(@IconFlags int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? JetgroovyIcons.Groovy.AbstractMethod : JetgroovyIcons.Groovy.Method;
    RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(methodIcon, this, false);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  @Override
  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return PsiImplUtil.getOrCreateTypeElement(getReturnTypeElementGroovy());
  }

  @Override
  @NotNull
  public GrParameterList getParameterList() {
    final GrParameterList parameterList = getStubOrPsiChild(GroovyElementTypes.PARAMETERS_LIST);
    LOG.assertTrue(parameterList != null);
    return parameterList;
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return ObjectUtils.assertNotNull(getStubOrPsiChild(GroovyElementTypes.THROW_CLAUSE));
  }

  @Override
  @Nullable
  public PsiCodeBlock getBody() {
    return PsiImplUtil.getOrCreatePsiCodeBlock(getBlock());
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(getParameters());
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);

    /*PsiClass containingClass = getContainingClass();

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    findSuperMethodRecursively(methods, containingClass, false, new HashSet<PsiClass>(), createMethodSignature(this),
                                new HashSet<MethodSignature>());

    return methods.toArray(new PsiMethod[methods.size()]);*/
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @Nullable
  public PsiMethod findDeepestSuperMethod() {
    final PsiMethod[] methods = findDeepestSuperMethods();
    if (methods.length > 0) return methods[0];
    return null;
  }

  @Override
  @NotNull
  public GrModifierList getModifierList() {
    return ObjectUtils.assertNotNull(getStubOrPsiChild(GroovyElementTypes.MODIFIERS));
  }

  @Override
  public boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public String getName() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return PsiImplUtil.getName(this);
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(nameElement.getProject());
    PsiElement newNameElement;
    if (PsiNameHelper.getInstance(getProject()).isIdentifier(name)) {
      try {
        GrMethod method = factory.createMethod(name, null);
        newNameElement = method.getNameIdentifierGroovy();
      }
      catch (IncorrectOperationException e) {
        newNameElement = factory.createLiteralFromValue(name).getFirstChild();
      }
    }
    else {
      newNameElement = factory.createLiteralFromValue(name).getFirstChild();
    }
    nameElement.replace(newNameElement);
    return this;
  }

  @Override
  public boolean hasTypeParameters() {
    return getTypeParameters().length > 0;
  }

  @Override
  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(GroovyElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  @NotNull
  public GrTypeParameter[] getTypeParameters() {
    final GrTypeParameterList list = getTypeParameterList();
    if (list != null) {
      return list.getTypeParameters();
    }

    return GrTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }


    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase)file).getScriptClass();
    }

    return null;
  }

  @Override
  @Nullable
  public GrDocComment getDocComment() {
    final GrMethodStub stub = getStub();
    if (stub != null && !stub.hasComment()) return null;
    return GrDocCommentUtil.findDocComment(this);
  }

  @Override
  public boolean isDeprecated() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecatedByDoc() || com.intellij.psi.impl.PsiImplUtil.isDeprecatedByAnnotation(this);
    }
    return com.intellij.psi.impl.PsiImplUtil.isDeprecatedByDocTag(this) || com.intellij.psi.impl.PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return com.intellij.psi.impl.PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass)containingClass.getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }


  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof GroovyFileImpl || parent instanceof GrTypeDefinitionBody) {
      super.delete();
      return;
    }
    throw new IncorrectOperationException("Invalid enclosing type definition");
  }

  @Override
  @NotNull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      String[] namedParameters = stub.getNamedParameters();
      if (namedParameters.length == 0) return Collections.emptyMap();

      Map<String, NamedArgumentDescriptor> result = ContainerUtil.newHashMap();

      for (String parameter : namedParameters) {
        result.put(parameter, GrNamedArgumentSearchVisitor.CODE_NAMED_ARGUMENTS_DESCR);
      }
      return result;
    }

    GrOpenBlock body = getBlock();
    if (body == null) return Collections.emptyMap();

    GrParameter[] parameters = getParameters();
    if (parameters.length == 0) return Collections.emptyMap();
    GrParameter firstParameter = parameters[0];

    PsiType type = firstParameter.getTypeGroovy();
    GrTypeElement typeElement = firstParameter.getTypeElementGroovy();
    //equalsToText can't be called here because of stub creating

    if (type != null && typeElement != null && type.getPresentableText() != null && !type.getPresentableText().endsWith("Map")) {
      return Collections.emptyMap();
    }

    GrNamedArgumentSearchVisitor visitor = new GrNamedArgumentSearchVisitor(firstParameter.getNameIdentifierGroovy().getText());

    body.accept(visitor);
    return visitor.getResult();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return GrReflectedMethodImpl.createReflectedMethods(this);
  }
}

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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInsight.GroovyTargetElementEvaluator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  public static final Key<Boolean> IS_RESOLVED_TO_GETTER = new Key<Boolean>("Is resolved to getter");
  private static final IElementType[] REFERENCE_NAME_TYPES = TokenSets.REFERENCE_NAMES.getTypes();

  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GroovyResolveResult[] resolveTypeOrProperty() {
    String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
                                           ? EnumSet.of(ClassHint.ResolveKind.CLASS, ClassHint.ResolveKind.PACKAGE)
                                           : EnumSet.of(ClassHint.ResolveKind.CLASS);
    boolean hasAt = hasAt();
    GroovyResolveResult[] classCandidates = GroovyResolveResult.EMPTY_ARRAY;
    if (!hasAt) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(getReferenceName(), this, kinds);
      resolveImpl(classProcessor);
      classCandidates = classProcessor.getCandidates();
      for (GroovyResolveResult classCandidate : classCandidates) {
        final PsiElement element = classCandidate.getElement();
        if (element instanceof PsiClass && ((PsiClass)element).isEnum()) {
          return classCandidates;
        }
      }
    }

    ResolverProcessor processor = new PropertyResolverProcessor(name, this);
    resolveImpl(processor);
    final GroovyResolveResult[] fieldCandidates = processor.getCandidates();

    if (hasAt) {
      return fieldCandidates;
    }

    //if reference expression is in class we need to return field instead of accessor method
    for (GroovyResolveResult candidate : fieldCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiTreeUtil.isAncestor(containingClass, this, true)) return fieldCandidates;
      } else {
        return fieldCandidates;
      }
    }

    final boolean isLValue = PsiUtil.isLValue(this);
    String[] names;
    names = isLValue ? GroovyPropertyUtils.suggestSettersName(name) : GroovyPropertyUtils.suggestGettersName(name);
    List<GroovyResolveResult> accessorResults = new ArrayList<GroovyResolveResult>();
    for (String getterName : names) {
      AccessorResolverProcessor accessorResolver = new AccessorResolverProcessor(getterName, this, !isLValue);
      resolveImpl(accessorResolver);
      final GroovyResolveResult[] candidates = accessorResolver.getCandidates(); //can be only one candidate
      if (candidates.length == 1 && candidates[0].isStaticsOK()) {
        return candidates;
      }
      else {
        ContainerUtil.addAll(accessorResults, candidates);
      }
    }
    if (fieldCandidates.length > 0) return fieldCandidates;
    if (accessorResults.size() > 0) return new GroovyResolveResult[]{accessorResults.get(0)};

    return classCandidates;
  }

  public GroovyResolveResult[] resolveMethodOrProperty() {
    return resolveMethodOrProperty(false, null);
  }

  public GroovyResolveResult[] getCallVariants(GrExpression upToArgument) {
    return resolveMethodOrProperty(true, upToArgument);
  }

  private GroovyResolveResult[] resolveMethodOrProperty(boolean allVariants, GrExpression upToArgument) {
    String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    final PsiType[] argTypes = PsiUtil.getArgumentTypes(this, false, upToArgument);
    MethodResolverProcessor methodResolver = runMethodResolverProcessor(argTypes, allVariants);
    assert methodResolver != null;

    final String[] names = GroovyPropertyUtils.suggestGettersName(name);
    List<GroovyResolveResult> list = new ArrayList<GroovyResolveResult>();
    for (String getterName : names) {
      AccessorResolverProcessor getterResolver = new AccessorResolverProcessor(getterName, this, true);
      resolveImpl(getterResolver);
      final GroovyResolveResult[] candidates = getterResolver.getCandidates(); //can be only one candidate
      if (!allVariants && candidates.length == 1 && candidates[0].isStaticsOK()) {
        if (methodResolver.hasApplicableCandidates()) return methodResolver.getCandidates();
        putUserData(IS_RESOLVED_TO_GETTER, true);
        return candidates;
      }
      else {
        ContainerUtil.addAll(list, candidates);
      }
    }

    PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(name, this);
    resolveImpl(propertyResolver);
    if (!allVariants) {
      final GroovyResolveResult[] propertyCandidates = propertyResolver.getCandidates();
      for (GroovyResolveResult candidate : propertyCandidates) {
        if (candidate.isStaticsOK() && candidate.isAccessible() && candidate.getElement() instanceof GrVariable &&
            !(candidate.getElement() instanceof GrField)) {
          return propertyResolver.getCandidates();
        }
      }
      if (methodResolver.hasApplicableCandidates()) return methodResolver.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
    }

    if (allVariants) {
      if (list.isEmpty()) ContainerUtil.addAll(list, propertyResolver.getCandidates());
      ContainerUtil.addAll(list, methodResolver.getCandidates());
      return list.toArray(new GroovyResolveResult[list.size()]);
    }

    if (methodResolver.hasCandidates()) {
      return methodResolver.getCandidates();
    }
    else if (list.size() > 0) {
      putUserData(IS_RESOLVED_TO_GETTER, true);
      return list.toArray(new GroovyResolveResult[list.size()]);
    }

    return GroovyResolveResult.EMPTY_ARRAY;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Nullable
  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    for (IElementType elementType : REFERENCE_NAME_TYPES) {
      if (lastChild.getElementType() == elementType) return lastChild.getPsi();
    }

    return null;
  }

  @NotNull
  public PsiReference getReference() {
    return this;
  }

  @Nullable
  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      if (nameElement.getNode().getElementType() == GroovyElementTypes.mSTRING_LITERAL ||
          nameElement.getNode().getElementType() == GroovyElementTypes.mGSTRING_LITERAL) {
        return GrStringUtil.removeQuotes(nameElement.getText());
      }

      return nameElement.getText();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
          final String newPropertyName = GroovyPropertyUtils.getPropertyNameByAccessorName(newElementName);
          if (newPropertyName != null && newPropertyName.length() > 0) {
            return doHandleElementRename(newPropertyName);
          } else {
            if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
              final PsiElement qualifier = getQualifier();
              String qualifierText = qualifier != null ? qualifier.getText() + '.' : "";
              return replace(
                GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(qualifierText + newElementName + "()"));
            }
            else {
              final PsiElement parent = getParent();
              if (parent instanceof GrAssignmentExpression) {
                final PsiElement qualifier = getQualifier();
                String qualifierText = qualifier != null ? qualifier.getText() + '.' : "";
                final GrExpression rValue = ((GrAssignmentExpression)parent).getRValue();
                String rValueText = rValue != null ? rValue.getText() : "";
                return parent.replace(
                  GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(
                    qualifierText + newElementName + "(" + rValueText + ")"));
              }
            }
          }
        }
      }
    } else if (resolved instanceof GrField && ((GrField) resolved).isProperty()) {
      final GrField field = (GrField) resolved;
      final String oldName = getReferenceName();
      if (oldName != null && !oldName.equals(field.getName())) { //was accessor reference to property
        if (oldName.startsWith("get")) {
          return doHandleElementRename("get" + StringUtil.capitalize(newElementName));
        } else if (oldName.startsWith("set")) {
          return doHandleElementRename("set" + StringUtil.capitalize(newElementName));
        }
      }
    }

    return doHandleElementRename(newElementName);
  }

  @Override
  protected PsiElement bindWithQualifiedRef(String qName) {
    final GrTypeArgumentList list = getTypeArgumentList();
    final String typeArgs = (list != null) ? list.getText() : "";
    final String text = qName + typeArgs;
    GrReferenceExpression qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(text);
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    PsiUtil.shortenReference(qualifiedRef);
    return qualifiedRef;
  }

  private PsiElement doHandleElementRename(String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteral(newElementName);
      getReferenceNameElement().replace(element);
      return this;
    }

    return super.handleElementRename(newElementName);
  }

  public int getTextOffset() {
    PsiElement parent = getParent();
    TextRange range = getTextRange();
    if (!(parent instanceof GrAssignmentExpression) || !this.equals(((GrAssignmentExpression) parent).getLValue())) {
      return range.getEndOffset(); //need this as a hack against TargetElementUtil
    }

    return range.getStartOffset();
  }

  public String toString() {
    return "Reference expression";
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final OurResolver RESOLVER = new OurResolver();

  private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

  public PsiType getNominalType() {
    return GroovyPsiManager.getInstance(getProject()).getTypeInferenceHelper().doWithInferenceDisabled(new Computable<PsiType>() {
      @Nullable
      public PsiType compute() {
        return getNominalTypeImpl();
      }
    });
  }

  @Nullable
  private PsiType getNominalTypeImpl() {
    IElementType dotType = getDotTokenType();

    final GroovyResolveResult resolveResult = advancedResolve();
    PsiElement resolved = resolveResult.getElement();
    if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
      if (resolved instanceof PsiMethod) {
        return GrClosureType.create((PsiMethod) resolved, resolveResult.getSubstitutor());
      }
      return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
    }
    PsiType result = null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    if (resolved == null && !"class".equals(getReferenceName())) {
      resolved = getReference().resolve();
    }
    if (resolved instanceof PsiClass) {
      if (getParent() instanceof GrReferenceExpression) {
        result = facade.getElementFactory().createType((PsiClass) resolved);
      } else {
        result = createJavaLangClassType(facade, facade.getElementFactory().createType((PsiClass)resolved));
      }
    } else if (resolved instanceof GrVariableBase) {
      result = ((GrVariableBase) resolved).getDeclaredType();
    } else if (resolved instanceof PsiVariable) {
      result = ((PsiVariable) resolved).getType();
    } else
    if (resolved instanceof PsiMethod && !GroovyPsiManager.isTypeBeingInferred(resolved)) {
      if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
        return facade.getElementFactory().createTypeByFQClassName(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
      }
      PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
        result = method.getParameterList().getParameters()[0].getType();
      } else {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) &&
                "getClass".equals(method.getName())) {
          result = getTypeForObjectGetClass(facade, method);
        } else {
          result = PsiUtil.getSmartReturnType(method);
        }

      }
    } else if (resolved instanceof GrReferenceExpression) {
      PsiElement parent = resolved.getParent();
      if (parent instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression) parent;
        if (resolved.equals(assignment.getLValue())) {
          GrExpression rValue = assignment.getRValue();
          if (rValue != null) {
            PsiType rType = rValue.getType();
            if (rType != null) result = rType;
          }
        }
      }
    } else if (resolved == null) {
      if ("class".equals(getReferenceName())) {
        result = createJavaLangClassType(JavaPsiFacade.getInstance(getProject()), JavaPsiFacade.getElementFactory(getProject())
          .createTypeByFQClassName(getText(), getResolveScope()));
      }
      else {
        GrExpression qualifier = getQualifierExpression();
        if (qualifier != null) {
          PsiType qType = qualifier.getType();
          if (qType instanceof PsiClassType) {
            PsiClassType.ClassResolveResult qResult = ((PsiClassType)qType).resolveGenerics();
            PsiClass clazz = qResult.getElement();
            if (clazz != null) {
              PsiClass mapClass = facade.findClass(CommonClassNames.JAVA_UTIL_MAP, getResolveScope());
              if (mapClass != null && mapClass.getTypeParameters().length == 2) {
                PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, qResult.getSubstitutor());
                if (substitutor != null) {
                  result = TypeConversionUtil.erasure(substitutor.substitute(mapClass.getTypeParameters()[1]));
                }
              }
            }
          }
        }
      }
    }

    if (result != null) {
      result = resolveResult.getSubstitutor().substitute(result);
      result = TypesUtil.boxPrimitiveType(result, getManager(), getResolveScope());
    }
    if (dotType != GroovyTokenTypes.mSPREAD_DOT) {
      return result;
    } else {
      return ResolveUtil.getListTypeForSpreadOperator(this, result);
    }
  }

  @Nullable
  private PsiType createJavaLangClassType(JavaPsiFacade facade, PsiClassType type) {
    PsiType result = null;
    PsiClass javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_CLASS, getResolveScope());
    if (javaLangClass != null) {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], type);
      }
      result = facade.getElementFactory().createType(javaLangClass, substitutor);
    }
    return result;
  }

  @Nullable
  private PsiType getTypeForObjectGetClass(JavaPsiFacade facade, PsiMethod method) {
    PsiType type = PsiUtil.getSmartReturnType(method);
    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType) type).resolve();
      if (clazz != null && CommonClassNames.JAVA_LANG_CLASS.equals(clazz.getQualifiedName())) {
        PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
        if (typeParameters.length == 1) {
          PsiClass qualifierClass = null;
          GrExpression qualifier = getQualifierExpression();
          if (qualifier != null) {
            PsiType qualifierType = qualifier.getType();
            if (qualifierType instanceof PsiClassType) {
              qualifierClass = ((PsiClassType) qualifierType).resolve();
            }
          } else {
            PsiNamedElement context = PsiTreeUtil.getParentOfType(this, PsiClass.class, GroovyFile.class);
            if (context instanceof PsiClass) qualifierClass = (PsiClass) context;
            else if (context instanceof GroovyFile) qualifierClass = ((GroovyFile) context).getScriptClass();
          }

          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          if (qualifierClass != null) {
            PsiType t = facade.getElementFactory().createType(qualifierClass);
            substitutor = substitutor.put(typeParameters[0], t);
          }
          return facade.getElementFactory().createType(clazz, substitutor);
        }
      }
    }
    return type;
  }

  private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    @Nullable
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      final PsiType inferred = GroovyPsiManager.getInstance(refExpr.getProject()).getTypeInferenceHelper().getInferredType(refExpr);
      final PsiType nominal = refExpr.getNominalTypeImpl();
      if (inferred == null || PsiType.NULL.equals(inferred)) {
        if (nominal == null) {
          /*inside nested closure we could still try to infer from variable initializer.
          * Not sound, but makes sense*/
          final PsiElement resolved = refExpr.resolve();
          if (resolved instanceof GrVariableBase) return ((GrVariableBase) resolved).getTypeGroovy();
        }

        return nominal;
      }

      if (nominal == null) return inferred;
      if (!TypeConversionUtil.isAssignable(nominal, inferred, false)) {
        final PsiElement resolved = refExpr.resolve();
        if (resolved instanceof GrVariable && ((GrVariable) resolved).getTypeElementGroovy() != null) {
          return nominal; //see GRVY-487
        }
      }
      return inferred;
    }
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  public String getName() {
    return getReferenceName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(name).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);

    return this;
  }

  private static class OurResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      String name = refExpr.getReferenceName();
      if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

      Kind kind = refExpr.getKind();
      if (incompleteCode) {
        ResolverProcessor processor = CompletionProcessor.createRefSameNameProcessor(refExpr, name);
        refExpr.resolveImpl(processor);
        GroovyResolveResult[] propertyCandidates = processor.getCandidates();
        if (propertyCandidates.length > 0) return propertyCandidates;
      }

      switch (kind) {
        case METHOD_OR_PROPERTY:
          return refExpr.resolveMethodOrProperty();
        case TYPE_OR_PROPERTY:
          return refExpr.resolveTypeOrProperty();
        default:
          return GroovyResolveResult.EMPTY_ARRAY;
      }
    }
  }

  private void resolveImpl(ResolverProcessor processor) {
    GrExpression qualifier = getQualifierExpression();
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(this, processor, true);
      if (!processor.hasCandidates()) {
        qualifier = PsiImplUtil.getRuntimeQualifier(this);
        if (qualifier != null) {
          processQualifier(processor, qualifier);
        }
      }
    } else {
      if (getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        processQualifier(processor, qualifier);
      } else {
        processQualifierForSpreadDot(processor, qualifier);
      }

      if (qualifier instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)qualifier).getReferenceName())) {
        processIfJavaLangClass(processor, qualifier.getType());
      } else if (qualifier instanceof GrThisReferenceExpression) {
        processIfJavaLangClass(processor, qualifier.getType());
      }
    }
  }

  private void processIfJavaLangClass(ResolverProcessor processor, PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
        final PsiType[] params = ((PsiClassType)type).getParameters();
        if (params.length == 1) {
          processClassQualifierType(processor, params[0]);
        }
      }
    }
  }

  private void processQualifierForSpreadDot(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass listClass = ResolveUtil.findListClass(getManager(), getResolveScope());
        if (listClass != null && listClass.getTypeParameters().length == 1) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(listClass, clazz, result.getSubstitutor());
          if (substitutor != null) {
            PsiType componentType = substitutor.substitute(listClass.getTypeParameters()[0]);
            if (componentType != null) {
              processClassQualifierType(processor, componentType);
            }
          }
        }
      }
    } else if (qualifierType instanceof PsiArrayType) {
      processClassQualifierType(processor, ((PsiArrayType) qualifierType).getComponentType());
    }
  }

  private void processQualifier(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          if (!resolved.processDeclarations(processor, ResolveState.initial(), null, this)) //noinspection UnnecessaryReturnStatement
            return;
        }
        else {
          qualifierType = JavaPsiFacade.getInstance(getProject()).getElementFactory()
            .createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, getResolveScope());
          processClassQualifierType(processor, qualifierType);
        }
      }
    } else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          processClassQualifierType(processor, conjunct);
        }
      } else {
        processClassQualifierType(processor, qualifierType);
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
          if (resolved instanceof PsiClass) { //omitted .class
            PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, getResolveScope());
            if (javaLangClass != null) {
              ResolveState state = ResolveState.initial();
              PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
              PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
              if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
              if (typeParameters.length == 1) {
                substitutor = substitutor.put(typeParameters[0], qualifierType);
                state = state.put(PsiSubstitutor.KEY, substitutor);
              }
              if (!javaLangClass.processDeclarations(processor, state, null, this)) return;
              PsiType javaLangClassType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(javaLangClass, substitutor);
              ResolveUtil.processNonCodeMethods(javaLangClassType, processor, this);
            }
          }
        }
      }
    }
  }

  private void processClassQualifierType(ResolverProcessor processor, PsiType qualifierType) {
    Project project = getProject();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType) qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      if (qualifierClass != null) {
        if (!qualifierClass.processDeclarations(processor,
                                                ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), null, this))
          return;
      }
      if (!ResolveUtil.processCategoryMembers(this, processor)) return;
    } else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
      if (!arrayClass.processDeclarations(processor, ResolveState.initial(), null, this)) return;
    } else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
        processClassQualifierType(processor, conjunct);
      }
      return;
    }

    ResolveUtil.processNonCodeMethods(qualifierType, processor, this);
  }

  @Nullable
  public MethodResolverProcessor runMethodResolverProcessor(PsiType[] argTypes, final boolean allVariants) {
    final String name = getReferenceName();
    if (name == null) {
      return null;
    }

    PsiType thisType = getThisType();

    MethodResolverProcessor methodResolver = new MethodResolverProcessor(name, this, false, thisType, argTypes, getTypeArguments(), allVariants);
    resolveImpl(methodResolver);
    return methodResolver;
  }

  private PsiType getThisType() {
    GrExpression qualifier = getQualifierExpression();
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
      if (qType != null) return qType;
    }

    return TypesUtil.getJavaLangObject(this);
  }


  enum Kind {
    TYPE_OR_PROPERTY,
    METHOD_OR_PROPERTY
  }

  Kind getKind() {
    if (getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER) return Kind.METHOD_OR_PROPERTY;

    PsiElement parent = getParent();
    if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
      return Kind.METHOD_OR_PROPERTY;
    }

    return Kind.TYPE_OR_PROPERTY;
  }

  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  public boolean hasAt() {
    return findChildByType(GroovyTokenTypes.mAT) != null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return getManager().areElementsEquivalent(element, GroovyTargetElementEvaluator.correctSearchTargets(resolve()));
    }
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }


  public boolean isSoft() {
    return false;
  }

  public GrExpression getQualifierExpression() {
    for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) return (GrExpression)cur;
    }
    return null;
  }

  public boolean isQualified() {
    return getQualifierExpression() != null;
  }

  @Nullable
  public PsiElement getDotToken() {
    return findChildByType(GroovyTokenTypes.DOTS);
  }

  public void replaceDotToken(PsiElement newDot) {
    if (newDot == null) return;
    if (!GroovyTokenTypes.DOTS.contains(newDot.getNode().getElementType())) return;
    final PsiElement oldDot = getDotToken();
    if (oldDot == null) return;

    getNode().replaceChild(oldDot.getNode(), newDot.getNode());
  }

  @Nullable
  public IElementType getDotTokenType() {
    PsiElement dot = getDotToken();
    return dot == null ? null : dot.getNode().getElementType();
  }

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    return (GroovyResolveResult[]) getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, incomplete);
  }

  public void processVariants(Consumer<Object> consumer) {
    CompleteReferenceExpression.processVariants(consumer, this);
  }

  @NotNull
  public GroovyResolveResult[] getSameNameVariants() {
    return RESOLVER.resolve(this, true);
  }

  public void setQualifierExpression(GrReferenceExpression newQualifier) {
    final GrExpression oldQualifier = getQualifierExpression();
    final ASTNode node = getNode();
    final PsiElement refNameElement = getReferenceNameElement();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        if (refNameElement != null) {
          node.removeRange(node.getFirstChildNode(), refNameElement.getNode());
        }
      }
    } else {
      if (oldQualifier != null) {
        node.replaceChild(oldQualifier.getNode(), newQualifier.getNode());
      } else {
        if (refNameElement != null) {
          node.addChild(newQualifier.getNode(), refNameElement.getNode());
          node.addLeaf(GroovyTokenTypes.mDOT, ".", refNameElement.getNode());
        }
      }
    }

  }

  public GrReferenceExpression bindToElementViaStaticImport(@NotNull PsiClass qualifierClass) {
    if (getQualifier() != null) {
      throw new IncorrectOperationException("Reference has qualifier");
    }

    if (StringUtil.isEmpty(getReferenceName())) {
      throw new IncorrectOperationException("Reference has empty name");
    }
    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFile) {
      final GrImportStatement statement = GroovyPsiElementFactory.getInstance(getProject())
        .createImportStatementFromText("import static " + qualifierClass.getQualifiedName() + "." + getReferenceName());
      ((GroovyFile)file).addImport(statement);
    }
    return this;
  }
}

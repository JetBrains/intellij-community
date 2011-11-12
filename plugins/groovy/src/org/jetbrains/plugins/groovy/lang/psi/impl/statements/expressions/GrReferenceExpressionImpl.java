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

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInsight.GroovyTargetElementEvaluator;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl<GrExpression> implements GrReferenceExpression {
  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  private boolean findClassOrPackageAtFirst() {
    final String name = getReferenceName();
    if (name == null || name.length() == 0 || hasAt()) return false;
    return Character.isUpperCase(name.charAt(0)) ||
           getParent() instanceof GrReferenceExpressionImpl && ((GrReferenceExpressionImpl)getParent()).findClassOrPackageAtFirst();
  }

  private boolean isDefinitelyKeyOfMap() {
    final GrExpression qualifier = ResolveUtil.getSelfOrWithQualifier(this);
    if (qualifier == null) return false;
    if (qualifier instanceof GrReferenceExpression) { //key in 'java.util.Map.key' is not access to map, it is access to static property of field
      final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
      if (resolved instanceof PsiClass) return false;
    }

    final PsiType type = qualifier.getType();
    if (type == null) return false;

    if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) return false;

    final String canonicalText = type.getCanonicalText();
    if (canonicalText.startsWith("java.")) return true;
    if (GroovyCommonClassNames.GROOVY_UTIL_CONFIG_OBJECT.equals(canonicalText)) return false;
    if (canonicalText.startsWith("groovy.")) return true;

    return false;
  }

  private GroovyResolveResult[] resolveTypeOrProperty() {
    if (isDefinitelyKeyOfMap()) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult[] results = resolveTypeOrPropertyInner();
    if (results.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    if (!ResolveUtil.mayBeKeyOfMap(this)) return results;

    //filter out all members from super classes. We should return only accessible members from map classes
    List<GroovyResolveResult> filtered = new ArrayList<GroovyResolveResult>();
    for (GroovyResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMember) {
        if (((PsiMember)element).hasModifierProperty(PsiModifier.PRIVATE)) continue;
        final PsiClass containingClass = ((PsiMember)element).getContainingClass();
        if (containingClass != null) {
          if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_MAP)) continue;
          final String name = containingClass.getQualifiedName();
          if (name != null && name.startsWith("java.")) continue;
          if (containingClass.getLanguage() != GroovyFileType.GROOVY_LANGUAGE && !InheritanceUtil.isInheritor(containingClass, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)) {
            continue;
          }
        }
      }
      filtered.add(result);
    }

    return ContainerUtil.toArray(filtered, new GroovyResolveResult[filtered.size()]);
  }

  private GroovyResolveResult[] resolveTypeOrPropertyInner() {
    String name = getReferenceName();

    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
                                           ? ResolverProcessor.RESOLVE_KINDS_CLASS_PACKAGE
                                           : ResolverProcessor.RESOLVE_KINDS_CLASS;

    GroovyResolveResult[] classCandidates = null;

    if (findClassOrPackageAtFirst()) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      GrReferenceResolveUtil.resolveImpl(classProcessor, this);
      classCandidates = classProcessor.getCandidates();
      if (classCandidates.length > 0) return classCandidates;
    }

    ResolverProcessor processor = new PropertyResolverProcessor(name, this);
    GrReferenceResolveUtil.resolveImpl(processor, this);
    final GroovyResolveResult[] fieldCandidates = processor.getCandidates();

    if (hasAt()) {
      return fieldCandidates;
    }

    //if reference expression is in class we need to return field instead of accessor method
    for (GroovyResolveResult candidate : fieldCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiTreeUtil.isContextAncestor(containingClass, this, true)) return fieldCandidates;
      }
      else {
        return fieldCandidates;
      }
    }

    final boolean isPropertyName = GroovyPropertyUtils.isPropertyName(name);

    final boolean isLValue = PsiUtil.isLValue(this);
    String[] accessorNames = isLValue ? GroovyPropertyUtils.suggestSettersName(name) : GroovyPropertyUtils.suggestGettersName(name);
    List<GroovyResolveResult> accessorResults = new ArrayList<GroovyResolveResult>();
    for (String accessorName : accessorNames) {
      AccessorResolverProcessor accessorResolver =
        new AccessorResolverProcessor(accessorName, this, !isLValue, false, getThisType(), getTypeArguments());
      GrReferenceResolveUtil.resolveImpl(accessorResolver, this);
      final GroovyResolveResult[] candidates = accessorResolver.getCandidates(); //can be only one correct candidate
      if (candidates.length > 0 && candidates[candidates.length - 1].isStaticsOK()) {
        if (isPropertyName || candidates[candidates.length - 1].getElement() instanceof GrAccessorMethod) {
          if (candidates.length == 1) return candidates;
          return new GroovyResolveResult[]{candidates[candidates.length - 1]};
        }
      }
      else {
        ContainerUtil.addAll(accessorResults, candidates);
      }
    }
    if (fieldCandidates.length > 0) return fieldCandidates;
    if (classCandidates == null) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      GrReferenceResolveUtil.resolveImpl(classProcessor, this);
      classCandidates = classProcessor.getCandidates();
    }
    if (classCandidates.length > 0) return classCandidates;
    if (accessorResults.size() > 0) return new GroovyResolveResult[]{accessorResults.get(0)};
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  public GroovyResolveResult[] getCallVariants(GrExpression upToArgument) {
    return resolveMethodOrProperty(true, upToArgument, true);
  }

  private void processMethods(final MethodResolverProcessor methodResolver) {
    GrReferenceResolveUtil.resolveImpl(methodResolver, this);
    if (methodResolver.hasApplicableCandidates()) {
      return;
    }

    // Search in ClosureMissingMethodContributor
    if (!isQualified() && getContext() instanceof GrMethodCall) {
      for (PsiElement e = this.getContext(); e != null; e = e.getContext()) {
        if (e instanceof GrClosableBlock) {
          ResolveState state = ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, (GrClosableBlock)e);
          for (ClosureMissingMethodContributor contributor : ClosureMissingMethodContributor.EP_NAME.getExtensions()) {
            if (!contributor.processMembers((GrClosableBlock)e, methodResolver, this, state)) {
              return;
            }
          }
        }
      }

    }
  }

  /**
   * priority: inside class C: local variable, c.method, c.property, c.getter
   *           in other places: local variable, c.method, c.getter, c.property
   */
  private GroovyResolveResult[] resolveMethodOrProperty(boolean allVariants, @Nullable GrExpression upToArgument, boolean genericsMatter) {
    final String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(name, this);
    GrReferenceResolveUtil.resolveImpl(propertyResolver, this);
    final GroovyResolveResult[] propertyCandidates = propertyResolver.getCandidates();

    if (!allVariants) { //search for local variables
      for (GroovyResolveResult candidate : propertyCandidates) {
        if (candidate.getElement() instanceof GrVariable && !(candidate.getElement() instanceof GrField)) {
          return propertyResolver.getCandidates();
        }
      }
    }

    final Pair<Boolean, GroovyResolveResult[]> shapeResults = resolveByShape(allVariants, upToArgument);
    if (!genericsMatter && !allVariants && shapeResults.first) {
      for (GroovyResolveResult candidate : shapeResults.second) {
        assert candidate.getElement().isValid();
      }
      return shapeResults.second;
    }

    MethodResolverProcessor methodResolver = null;
    if (genericsMatter) {
      methodResolver = createMethodProcessor(allVariants, name, false, upToArgument);

      for (GroovyResolveResult result : shapeResults.second) {
        final ResolveState state = ResolveState.initial().
          put(PsiSubstitutor.KEY, result.getSubstitutor()).
          put(ResolverProcessor.RESOLVE_CONTEXT, result.getCurrentFileResolveContext());
        methodResolver.execute(result.getElement(), state);
      }

      if (!allVariants && methodResolver.hasApplicableCandidates()) {
        return methodResolver.getCandidates();
      }
    }

    //search for fields inside its class
    if (!allVariants) {
      for (GroovyResolveResult candidate : propertyCandidates) {
        final PsiElement element = candidate.getElement();
        if (element instanceof GrField) {
          final PsiClass containingClass = ((PsiField)element).getContainingClass();
          if (containingClass != null && PsiTreeUtil.isContextAncestor(containingClass, this, true)) return propertyCandidates;
        }
      }
    }

    List<GroovyResolveResult> allCandidates = new ArrayList<GroovyResolveResult>();
    ContainerUtil.addAll(allCandidates, propertyCandidates);
    ContainerUtil.addAll(allCandidates, genericsMatter ? methodResolver.getCandidates() : shapeResults.second);

    //search for getters
    for (String getterName : GroovyPropertyUtils.suggestGettersName(name)) {
      AccessorResolverProcessor getterResolver = new AccessorResolverProcessor(getterName, this, true, genericsMatter, getThisType(), getTypeArguments());
      GrReferenceResolveUtil.resolveImpl(getterResolver, this);
      final GroovyResolveResult[] candidates = getterResolver.getCandidates(); //can be only one candidate
      if (!allVariants && candidates.length == 1) {
        return candidates;
      }
      ContainerUtil.addAll(allCandidates, candidates);
    }

    if (allCandidates.size() > 0) {
      return allCandidates.toArray(new GroovyResolveResult[allCandidates.size()]);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private Pair<Boolean, GroovyResolveResult[]> resolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    if (allVariants) {
      return doResolveByShape(allVariants, upToArgument);
    }

    assert upToArgument == null;

    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<Pair<Boolean, GroovyResolveResult[]>>() {
      @Override
      public Result<Pair<Boolean, GroovyResolveResult[]>> compute() {
        return Result.create(doResolveByShape(false, null), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  private Pair<Boolean, GroovyResolveResult[]> doResolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    final String name = getReferenceName();
    assert name != null;

    final MethodResolverProcessor shapeProcessor = createMethodProcessor(allVariants, name, true, upToArgument);
    processMethods(shapeProcessor);
    GroovyResolveResult[] candidates = shapeProcessor.getCandidates();
    for (GroovyResolveResult candidate : candidates) {
      assert candidate.getElement().isValid();
    }
    return Pair.create(shapeProcessor.hasApplicableCandidates(), candidates);
  }

  private MethodResolverProcessor createMethodProcessor(boolean allVariants, String name, final boolean byShape, @Nullable GrExpression upToArgument) {
    final PsiType[] argTypes = PsiUtil.getArgumentTypes(this, false, upToArgument);
    if (byShape && argTypes != null) {
      for (int i = 0; i < argTypes.length; i++) {
        argTypes[i] = TypeConversionUtil.erasure(argTypes[i]);
      }
    }
    return new MethodResolverProcessor(name, this, false, getThisType(), argTypes, getTypeArguments(), allVariants, byShape);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Nullable
  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    if (TokenSets.REFERENCE_NAMES.contains(lastChild.getElementType())) {
      return lastChild.getPsi();
    }

    return null;
  }

  @NotNull
  public PsiReference getReference() {
    return this;
  }

  @Nullable
  public GrExpression getQualifier() {
    return getQualifierExpression();
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      IElementType nodeType = nameElement.getNode().getElementType();
      if (nodeType == mSTRING_LITERAL || nodeType == mGSTRING_LITERAL) {
        return GrStringUtil.removeQuotes(nameElement.getText());
      }

      return nameElement.getText();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return handleElementRenameInner(newElementName);
  }

  @Override
  protected GrReferenceExpression bindWithQualifiedRef(@NotNull String qName) {
    final GrTypeArgumentList list = getTypeArgumentList();
    final String typeArgs = (list != null) ? list.getText() : "";
    final String text = qName + typeArgs;
    GrReferenceExpression qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(text);
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    return qualifiedRef;
  }

  @Override
  public boolean isFullyQualified() {
    final Kind kind = getKind();
    switch (kind) {
      case TYPE_OR_PROPERTY:
      case METHOD_OR_PROPERTY_OR_TYPE:
        if (resolve() instanceof PsiPackage) return true;
        break;
      case METHOD_OR_PROPERTY:
        break;

    }
    final GrExpression qualifier = getQualifier();
    if (!(qualifier instanceof GrReferenceExpressionImpl)) return false;
    return ((GrReferenceExpressionImpl)qualifier).isFullyQualified();
  }

  protected PsiElement handleElementRenameInner(String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteral(newElementName);
      getReferenceNameElement().replace(element);
      return this;
    }

    return super.handleElementRenameInner(newElementName);
  }

  public String toString() {
    return "Reference expression";
  }

  @Nullable
  public PsiElement resolve() {
    final GroovyResolveResult[] results = resolveByShape();
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  public GroovyResolveResult[] resolveByShape() {
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<GroovyResolveResult[]>() {
      @Override
      public Result<GroovyResolveResult[]> compute() {
        GroovyResolveResult[] value = RecursionManager.doPreventingRecursion(GrReferenceExpressionImpl.this, true, new Computable<GroovyResolveResult[]>() {
          @Override
          public GroovyResolveResult[] compute() {
            return doPolyResolve(false, false);
          }
        });
        if (value == null) {
          value = GroovyResolveResult.EMPTY_ARRAY;
        }
        return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  private static final ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> POLY_RESOLVER =
    new ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl>() {
      public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
        return refExpr.doPolyResolve(incompleteCode, true);
      }
    };
  private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

  public PsiType getNominalType() {
    return getNominalTypeImpl();
  }

  @Nullable
  private PsiType getNominalTypeImpl() {
    IElementType dotType = getDotTokenType();

    final GroovyResolveResult resolveResult = advancedResolve();
    PsiElement resolved = resolveResult.getElement();

    for (GrReferenceTypeEnhancer enhancer : GrReferenceTypeEnhancer.EP_NAME.getExtensions()) {
      PsiType type = enhancer.getReferenceType(this, resolved);
      if (type != null) {
        return type;
      }
    }

    if (dotType == mMEMBER_POINTER) {
      if (resolved instanceof PsiMethod) {
        return GrClosureType.create((PsiMethod) resolved, resolveResult.getSubstitutor());
      }
      return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, this);
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
        result = TypesUtil
          .createJavaLangClassType(facade.getElementFactory().createType((PsiClass)resolved), getProject(), getResolveScope());
      }
    } else if (resolved instanceof GrVariable) {
      result = ((GrVariable) resolved).getDeclaredType();
    } else if (resolved instanceof PsiVariable) {
      result = ((PsiVariable) resolved).getType();
    } else
    if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
        result = method.getParameterList().getParameters()[0].getType();
      } else {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) &&
                "getClass".equals(method.getName())) {
          result = TypesUtil.createJavaLangClassType(getQualifierType(), getProject(), getResolveScope());
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
    }
    else if (resolved == null) {
      GrExpression qualifier = getQualifierExpression();
      if ("class".equals(getReferenceName())) {
        result = TypesUtil.createJavaLangClassType(getQualifierType(), getProject(), getResolveScope());
      }
      else {
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
      result = PsiImplUtil.normalizeWildcardTypeByPosition(result, this);
    }
    if (dotType != mSPREAD_DOT) {
      return result;
    } else {
      return ResolveUtil.getListTypeForSpreadOperator(this, result);
    }
  }

  @Nullable
  private PsiType getQualifierType() {
    final GrExpression qualifier = getQualifierExpression();
    if (qualifier == null) {
      final PsiElement context = PsiUtil.getFileOrClassContext(this);
      PsiClass contextClass = null;
      if (context instanceof PsiClass) {
        contextClass = (PsiClass)context;
      }
      else if (context instanceof GroovyFile) contextClass = ((GroovyFile)context).getScriptClass();
      if (contextClass == null) return null;
      return JavaPsiFacade.getElementFactory(getProject()).createType(contextClass);
    }
    else {
      return qualifier.getType();
    }
  }


  private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    @Nullable
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      final PsiType inferred = TypeInferenceHelper.getInferredType(refExpr);
      final PsiType nominal = refExpr.getNominalTypeImpl();
      if (inferred == null || PsiType.NULL.equals(inferred)) {
        if (nominal == null) {
          //inside nested closure we could still try to infer from variable initializer. Not sound, but makes sense
          if (!refExpr.isValid()) {
            throw new AssertionError("invalid reference");
          }
          final PsiElement resolved = refExpr.resolve();
          if (resolved instanceof GrVariable) {
            if (!resolved.isValid()) {
              throw new AssertionError("Invalid target of a valid reference");
            }
            return ((GrVariable) resolved).getTypeGroovy();
          }
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

  private GroovyResolveResult[] doPolyResolve(boolean incompleteCode, boolean genericsMatter) {
    String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (incompleteCode) {
      ResolverProcessor processor = CompletionProcessor.createRefSameNameProcessor(this, name);
      GrReferenceResolveUtil.resolveImpl(processor, this);
      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
    }

    switch (getKind()) {
      case METHOD_OR_PROPERTY:
        return resolveMethodOrProperty(false, null, genericsMatter);
      case TYPE_OR_PROPERTY:
        return resolveTypeOrProperty();
      case METHOD_OR_PROPERTY_OR_TYPE:
        GroovyResolveResult[] results = resolveMethodOrProperty(false, null, genericsMatter);
        if (results.length == 0) results = resolveTypeOrProperty();
        return results;
      default:
        return GroovyResolveResult.EMPTY_ARRAY;
    }
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
    METHOD_OR_PROPERTY,
    METHOD_OR_PROPERTY_OR_TYPE
  }

  Kind getKind() {
    if (getDotTokenType() == mMEMBER_POINTER) return Kind.METHOD_OR_PROPERTY;

    PsiElement parent = getParent();
    if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
      return Kind.METHOD_OR_PROPERTY_OR_TYPE;
    }

    return Kind.TYPE_OR_PROPERTY;
  }

  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  public boolean hasAt() {
    return findChildByType(mAT) != null;
  }

  @Override
  public boolean hasMemberPointer() {
    return findChildByType(mMEMBER_POINTER) != null;
  }

  public boolean isReferenceTo(PsiElement element) {
    PsiElement target = GroovyTargetElementEvaluator.correctSearchTargets(resolve());
    if (getManager().areElementsEquivalent(element, target)) {
      return true;
    }

    if (element instanceof PsiMethod && target instanceof PsiMethod) {
      PsiMethod[] superMethods = ((PsiMethod)target).findSuperMethods(false);
      if (Arrays.asList(superMethods).contains(element)) {
        return true;
      }
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
    return findExpressionChild(this);
  }

  public boolean isQualified() {
    return getQualifierExpression() != null;
  }

  @Nullable
  public PsiElement getDotToken() {
    return findChildByType(TokenSets.DOTS);
  }

  public void replaceDotToken(PsiElement newDot) {
    if (newDot == null) return;
    if (!TokenSets.DOTS.contains(newDot.getNode().getElementType())) return;
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
    ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, POLY_RESOLVER, true, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    final ResolveResult[] results = ResolveCache.getInstance(getProject()).resolveWithCaching(this, POLY_RESOLVER, true, incomplete);
    return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY :  (GroovyResolveResult[])results;
  }

  @Override
  public void processVariants(PrefixMatcher matcher, CompletionParameters parameters, Consumer<Object> consumer) {
    CompleteReferenceExpression.processVariants(matcher, consumer, this, parameters);
  }

  @NotNull
  public GroovyResolveResult[] getSameNameVariants() {
    return doPolyResolve(true, true);
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

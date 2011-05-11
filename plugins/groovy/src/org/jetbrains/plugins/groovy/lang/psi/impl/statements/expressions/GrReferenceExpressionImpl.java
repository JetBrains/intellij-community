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

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
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
import org.jetbrains.plugins.groovy.codeInsight.GroovyTargetElementEvaluator;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
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

  private boolean checkForMapKey() {
    final GrExpression qualifier = getQualifierExpression();
    if (qualifier == null) return false;
    if (qualifier instanceof GrReferenceExpression) { //key in 'java.util.Map.key' is not access to map, it is access to static property of field
      final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
      if (resolved instanceof PsiClass) return false;
    }

    return InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_MAP);
  }

  public GroovyResolveResult[] resolveTypeOrProperty() {
    String name = getReferenceName();

    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (checkForMapKey()) return GroovyResolveResult.EMPTY_ARRAY;

    EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
                                           ? ResolverProcessor.RESOLVE_KINDS_CLASS_PACKAGE
                                           : ResolverProcessor.RESOLVE_KINDS_CLASS;

    GroovyResolveResult[] classCandidates = null;

    if (findClassOrPackageAtFirst()) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      resolveImpl(classProcessor);
      classCandidates = classProcessor.getCandidates();
      if (classCandidates.length > 0) return classCandidates;
    }

    ResolverProcessor processor = new PropertyResolverProcessor(name, this);
    resolveImpl(processor);
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
      AccessorResolverProcessor accessorResolver = new AccessorResolverProcessor(accessorName, this, !isLValue);
      resolveImpl(accessorResolver);
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
      resolveImpl(classProcessor);
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
    resolveImpl(methodResolver);
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
    resolveImpl(propertyResolver);
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

    final MethodResolverProcessor methodResolver = createMethodProcessor(allVariants, name, !genericsMatter, upToArgument);

    for (GroovyResolveResult result : shapeResults.second) {
      final ResolveState state = ResolveState.initial().
        put(PsiSubstitutor.KEY, result.getSubstitutor()).
        put(ResolverProcessor.RESOLVE_CONTEXT, result.getCurrentFileResolveContext());
      methodResolver.execute(result.getElement(), state);
    }

    if (!allVariants && methodResolver.hasApplicableCandidates()) {
      return methodResolver.getCandidates();
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
    ContainerUtil.addAll(allCandidates, methodResolver.getCandidates());

    //search for getters
    for (String getterName : GroovyPropertyUtils.suggestGettersName(name)) {
      AccessorResolverProcessor getterResolver = new AccessorResolverProcessor(getterName, this, true);
      resolveImpl(getterResolver);
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
    /*
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
          final String newPropertyName = GroovyPropertyUtils.getPropertyNameByAccessorName(newElementName);
          if (newPropertyName != null && newPropertyName.length() > 0) {
            return handleElementRenameInner(newPropertyName);
          } else {
            if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
              final PsiElement qualifier = getQualifier();
              String qualifierText = qualifier != null ? qualifier.getText() + '.' : "";
              final GrMethodCallExpression replaced =
                (GrMethodCallExpression)replace(
                  GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(qualifierText + newElementName + "()"));
              return replaced.getInvokedExpression();
            }
            else {
              final PsiElement parent = getParent();
              if (parent instanceof GrAssignmentExpression) {
                final PsiElement qualifier = getQualifier();
                String qualifierText = qualifier != null ? qualifier.getText() + '.' : "";
                final GrExpression rValue = ((GrAssignmentExpression)parent).getRValue();
                String rValueText = rValue != null ? rValue.getText() : "";
                final GrMethodCallExpression replaced = ((GrMethodCallExpression)parent.replace(
                  GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(
                    qualifierText + newElementName + "(" + rValueText + ")")));
                return replaced.getInvokedExpression();
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
          return handleElementRenameInner("get" + StringUtil.capitalize(newElementName));
        } else if (oldName.startsWith("set")) {
          return handleElementRenameInner("set" + StringUtil.capitalize(newElementName));
        }
      }
    }
*/

    return handleElementRenameInner(newElementName);
  }

  @Override
  protected PsiElement bindWithQualifiedRef(@NotNull String qName) {
    final GrTypeArgumentList list = getTypeArgumentList();
    final String typeArgs = (list != null) ? list.getText() : "";
    final String text = qName + typeArgs;
    GrReferenceExpression qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(text);
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    PsiUtil.shortenReference(qualifiedRef);
    return qualifiedRef;
  }

  protected PsiElement handleElementRenameInner(String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteral(newElementName);
      getReferenceNameElement().replace(element);
      return this;
    }

    return super.handleElementRenameInner(newElementName);
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
    final GroovyResolveResult[] results = resolveByShape();
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  public GroovyResolveResult[] resolveByShape() {
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<GroovyResolveResult[]>() {
      @Override
      public Result<GroovyResolveResult[]> compute() {
        return Result.create(doPolyResolve(false, false), PsiModificationTracker.MODIFICATION_COUNT);
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
          final GrExpression qualifier = getQualifier();
          if (PsiUtil.seemsToBeQualifiedClassName(qualifier)) {
            result = TypesUtil.createJavaLangClassType(facade.getElementFactory().createTypeFromText(qualifier.getText(), this),
                                                       getProject(), getResolveScope());
          }
          else {
            result = getTypeForObjectGetClass(method);
          }
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
        if (PsiUtil.seemsToBeQualifiedClassName(qualifier)) {
          assert qualifier != null;
          result = TypesUtil.createJavaLangClassType(facade.getElementFactory().createTypeFromText(qualifier.getText(), this), getProject(),
                                                     getResolveScope());
        } else {
          result = TypesUtil.createJavaLangClassType(getQualifierType(), getProject(), getResolveScope());
        }
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
      final PsiNamedElement context = PsiTreeUtil.getParentOfType(this, GroovyFile.class, PsiClass.class);
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

  @Nullable
  private PsiType getTypeForObjectGetClass(PsiMethod method) {
    PsiType type = PsiUtil.getSmartReturnType(method);
    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType)type).resolve();
      if (clazz != null && CommonClassNames.JAVA_LANG_CLASS.equals(clazz.getQualifiedName())) {
        PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
        if (typeParameters.length == 1) {
          return TypesUtil.createJavaLangClassType(getQualifierType(), getProject(), getResolveScope());
        }
      }
    }
    return type;
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
      resolveImpl(processor);
      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
    }

    switch (getKind()) {
      case METHOD_OR_PROPERTY:
        return resolveMethodOrProperty(false, null, genericsMatter);
      case TYPE_OR_PROPERTY:
        return resolveTypeOrProperty();
      default:
        return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  private boolean resolveImpl(ResolverProcessor processor) {
    GrExpression qualifier = getQualifierExpression();
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(this, processor, true);
      if (!processor.hasCandidates()) {
        qualifier = PsiImplUtil.getRuntimeQualifier(this);
        if (qualifier != null) {
          if (!processQualifier(processor, qualifier)) return false;
        }
      }
    } else {
      if (getDotTokenType() != mSPREAD_DOT) {
        if (!processQualifier(processor, qualifier)) return false;
      } else {
        if (!processQualifierForSpreadDot(processor, qualifier)) return false;
      }

      if (qualifier instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)qualifier).getReferenceName()) ||
          qualifier instanceof GrThisReferenceExpression) {
        return processIfJavaLangClass(processor, qualifier.getType(), qualifier);
      }
    }
    return true;
  }

  private boolean processIfJavaLangClass(ResolverProcessor processor, PsiType type, GroovyPsiElement resolveContext) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
        final PsiType[] params = ((PsiClassType)type).getParameters();
        if (params.length == 1) {
          if (!processClassQualifierType(processor, params[0], resolveContext)) return false;
        }
      }
    }
    return true;
  }

  private boolean processQualifierForSpreadDot(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass collection =
          JavaPsiFacade.getInstance(getManager().getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, getResolveScope());
        if (collection != null && collection.getTypeParameters().length == 1) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(collection, clazz, result.getSubstitutor());
          if (substitutor != null) {
            PsiType componentType = substitutor.substitute(collection.getTypeParameters()[0]);
            if (componentType != null) {
              return processClassQualifierType(processor, componentType, qualifier);
            }
          }
        }
      }
    } else if (qualifierType instanceof PsiArrayType) {
      return processClassQualifierType(processor, ((PsiArrayType) qualifierType).getComponentType(), qualifier);
    }
    return true;
  }

  private boolean processQualifier(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          return resolved.processDeclarations(processor, ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, qualifier), null,
                                              this);
        }
        else {
          qualifierType = TypesUtil.getJavaLangObject(this);
          return processClassQualifierType(processor, qualifierType, qualifier);
        }
      }
    } else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          if (!processClassQualifierType(processor, conjunct, qualifier)) return false;
        }
      } else {
        if (!processClassQualifierType(processor, qualifierType, qualifier)) return false;
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
              if (!javaLangClass.processDeclarations(processor, state, null, this)) return false;
              PsiType javaLangClassType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(javaLangClass, substitutor);
              if (!ResolveUtil.processNonCodeMethods(javaLangClassType, processor, this, state)) return false;
            }
          }
        }
      }
    }
    return true;
  }

  private boolean processClassQualifierType(ResolverProcessor processor, PsiType qualifierType, GroovyPsiElement resolveContext) {
    final ResolveState state;
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType)qualifierType).resolveGenerics();
      PsiClass qualifierClass = qualifierResult.getElement();
      state = ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor())
        .put(ResolverProcessor.RESOLVE_CONTEXT, resolveContext);
      if (qualifierClass != null) {
        if (!qualifierClass.processDeclarations(processor, state, null, this)) return false;
      }
    }
    else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(getProject()).getArrayClass();
      state = ResolveState.initial();
      if (!arrayClass.processDeclarations(processor, state, null, this)) return false;
    }
    else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)qualifierType).getConjuncts()) {
        if (!processClassQualifierType(processor, conjunct, resolveContext)) return false;
      }
      return true;
    } else {
      state = ResolveState.initial();
    }

    if (!ResolveUtil.processCategoryMembers(this, processor)) return false;
    return ResolveUtil.processNonCodeMethods(qualifierType, processor, this, state);
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
    if (getDotTokenType() == mMEMBER_POINTER) return Kind.METHOD_OR_PROPERTY;

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
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, POLY_RESOLVER, true, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    final ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, POLY_RESOLVER, true, incomplete);
    return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY :  (GroovyResolveResult[])results;
  }

  @Override
  public void processVariants(PrefixMatcher matcher, Consumer<Object> consumer) {
    CompleteReferenceExpression.processVariants(matcher, consumer, this);
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

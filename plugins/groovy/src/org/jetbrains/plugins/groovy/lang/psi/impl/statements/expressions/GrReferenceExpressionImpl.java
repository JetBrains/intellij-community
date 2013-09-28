/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInsight.GrReassignedLocalVarsChecker;
import org.jetbrains.plugins.groovy.codeInsight.GroovyTargetElementEvaluator;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;
import org.jetbrains.plugins.groovy.util.ResolveProfiler;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mAT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMEMBER_POINTER;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl<GrExpression> implements GrReferenceExpression {

  private static final Logger LOG = Logger.getInstance(GrReferenceExpressionImpl.class);

  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  private boolean findClassOrPackageAtFirst() {
    final String name = getReferenceName();
    if (StringUtil.isEmpty(name) || hasAt()) return false;

    return Character.isUpperCase(name.charAt(0)) && !isMethodCallRef() ||
           getParent() instanceof GrReferenceExpressionImpl && ((GrReferenceExpressionImpl)getParent()).findClassOrPackageAtFirst();
  }

  private boolean isMethodCallRef() {
    final PsiElement parent = getParent();
    return parent instanceof GrMethodCall ||
           parent instanceof GrReferenceExpressionImpl && ((GrReferenceExpressionImpl)parent).isMethodCallRef();
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
          if (containingClass.getLanguage() != GroovyFileType.GROOVY_LANGUAGE &&
              !InheritanceUtil.isInheritor(containingClass, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)) {
            continue;
          }
        }
      }
      filtered.add(result);
    }

    return ContainerUtil.toArray(filtered, new GroovyResolveResult[filtered.size()]);
  }

  private GroovyResolveResult[] resolveTypeOrPropertyInner() {
    PsiElement nameElement = getReferenceNameElement();
    String name = getReferenceName();

    if (name == null || nameElement == null) return GroovyResolveResult.EMPTY_ARRAY;

    IElementType nameType = nameElement.getNode().getElementType();
    if (nameType == GroovyTokenTypes.kTHIS) {
      ArrayList<GroovyResolveResult> results = new ArrayList<GroovyResolveResult>();
      if (GrReferenceResolveUtil.resolveThisExpression(this, results)) {
        return results.toArray(new GroovyResolveResult[results.size()]);
      }
    }
    else if (nameType == GroovyTokenTypes.kSUPER) {
      ArrayList<GroovyResolveResult> results = new ArrayList<GroovyResolveResult>();
      if (GrReferenceResolveUtil.resolveSuperExpression(this, results)) {
        return results.toArray(new GroovyResolveResult[results.size()]);
      }
    }


    EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
                                           ? ResolverProcessor.RESOLVE_KINDS_CLASS_PACKAGE
                                           : ResolverProcessor.RESOLVE_KINDS_CLASS;

    GroovyResolveResult[] classCandidates = null;

    ResolverProcessor processor = new PropertyResolverProcessor(name, this);
    GrReferenceResolveUtil.resolveImpl(processor, this);
    final GroovyResolveResult[] fieldCandidates = processor.getCandidates();

    if (hasAt()) {
      return fieldCandidates;
    }


    boolean canBeClassOrPackage = ResolveUtil.canBeClassOrPackage(this);

    if (canBeClassOrPackage && findClassOrPackageAtFirst()) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      GrReferenceResolveUtil.resolveImpl(classProcessor, this);
      classCandidates = classProcessor.getCandidates();
      if (classCandidates.length > 0 && containsPackage(classCandidates)) return classCandidates;
    }

    //if reference expression is in class we need to return field instead of accessor method
    for (GroovyResolveResult candidate : fieldCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiTreeUtil.isContextAncestor(containingClass, this, true)) return fieldCandidates;
      }
      else if (!(element instanceof GrBindingVariable)) {
        return fieldCandidates;
      }
    }

    if (classCandidates != null && classCandidates.length > 0) return classCandidates;

    final boolean isLValue = PsiUtil.isLValue(this);
    String[] accessorNames = isLValue ? GroovyPropertyUtils.suggestSettersName(name) : GroovyPropertyUtils.suggestGettersName(name);
    List<GroovyResolveResult> accessorResults = new ArrayList<GroovyResolveResult>();
    for (String accessorName : accessorNames) {
      AccessorResolverProcessor accessorResolver =
        new AccessorResolverProcessor(accessorName, name, this, !isLValue, false, GrReferenceResolveUtil.getQualifierType(this),
                                      getTypeArguments());
      GrReferenceResolveUtil.resolveImpl(accessorResolver, this);
      final GroovyResolveResult[] candidates = accessorResolver.getCandidates();

      //can be only one correct candidate or some incorrect
      if (candidates.length == 1 && candidates[0].isStaticsOK() && candidates[0].isAccessible()) {
        return candidates;
      }
      else {
        ContainerUtil.addAll(accessorResults, candidates);
      }
    }

    final ArrayList<GroovyResolveResult> fieldList = ContainerUtil.newArrayList(fieldCandidates);
    filterOutBindings(fieldList);
    if (!fieldList.isEmpty()) {
      return fieldList.toArray(new GroovyResolveResult[fieldList.size()]);
    }

    if (classCandidates == null && canBeClassOrPackage ) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      GrReferenceResolveUtil.resolveImpl(classProcessor, this);
      classCandidates = classProcessor.getCandidates();
    }
    if (classCandidates != null && classCandidates.length > 0) return classCandidates;
    if (accessorResults.size() > 0) return new GroovyResolveResult[]{accessorResults.get(0)};
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static boolean containsPackage(@NotNull GroovyResolveResult[] candidates) {
    for (GroovyResolveResult candidate : candidates) {
      if (candidate.getElement() instanceof PsiPackage) return true;
    }
    return false;
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
          ResolveState state = ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, e);
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
   * in other places: local variable, c.method, c.getter, c.property
   */
  @NotNull
  private GroovyResolveResult[] resolveMethodOrProperty(boolean allVariants, @Nullable GrExpression upToArgument, boolean genericsMatter) {
    final String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(name, this);
    GrReferenceResolveUtil.resolveImpl(propertyResolver, this);
    final GroovyResolveResult[] propertyCandidates = propertyResolver.getCandidates();

    if (!allVariants) { //search for local variables
      for (GroovyResolveResult candidate : propertyCandidates) {
        final PsiElement element = candidate.getElement();
        if (element instanceof GrVariable && !(element instanceof GrField || element instanceof GrBindingVariable)) {
          return propertyCandidates;
        }
      }
    }

    final Pair<Boolean, GroovyResolveResult[]> shapeResults = resolveByShape(allVariants, upToArgument);
    if (!genericsMatter && !allVariants && shapeResults.first) {
      assertAllAreValid(shapeResults.second);
      return shapeResults.second;
    }

    MethodResolverProcessor methodResolver = null;
    if (genericsMatter) {
      methodResolver = createMethodProcessor(allVariants, name, false, upToArgument);

      for (GroovyResolveResult result : shapeResults.second) {
        final ResolveState state = ResolveState.initial().
          put(PsiSubstitutor.KEY, result.getSubstitutor()).
          put(ResolverProcessor.RESOLVE_CONTEXT, result.getCurrentFileResolveContext()).
          put(SpreadState.SPREAD_STATE, result.getSpreadState());
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

    filterOutBindings(allCandidates);

    //search for getters
    for (String getterName : GroovyPropertyUtils.suggestGettersName(name)) {
      AccessorResolverProcessor getterResolver =
        new AccessorResolverProcessor(getterName, name, this, true, genericsMatter, GrReferenceResolveUtil.getQualifierType(this), getTypeArguments());
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

  private static void filterOutBindings(List<GroovyResolveResult> candidates) {
    boolean hasNonBinding = false;
    for (GroovyResolveResult candidate : candidates) {
      if (!(candidate.getElement() instanceof GrBindingVariable)) {
        hasNonBinding = true;
      }
    }

    if (hasNonBinding) {
      for (Iterator<GroovyResolveResult> iterator = candidates.iterator(); iterator.hasNext(); ) {
        GroovyResolveResult candidate = iterator.next();
        if (candidate.getElement() instanceof GrBindingVariable) {
          iterator.remove();
        }
      }
    }
  }

  private Pair<Boolean, GroovyResolveResult[]> resolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    if (allVariants) {
      return doResolveByShape(allVariants, upToArgument);
    }

    LOG.assertTrue(upToArgument == null);

    return TypeInferenceHelper.getCurrentContext().getCachedValue(this, new NullableComputable<Pair<Boolean, GroovyResolveResult[]>>() {
      @Override
      public Pair<Boolean, GroovyResolveResult[]> compute() {
        return doResolveByShape(false, null);
      }
    });
  }

  private Pair<Boolean, GroovyResolveResult[]> doResolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    final String name = getReferenceName();
    LOG.assertTrue(name != null);

    final MethodResolverProcessor shapeProcessor = createMethodProcessor(allVariants, name, true, upToArgument);
    processMethods(shapeProcessor);
    GroovyResolveResult[] candidates = shapeProcessor.getCandidates();
    assertAllAreValid(candidates);
    return Pair.create(shapeProcessor.hasApplicableCandidates(), candidates);
  }

  private static void assertAllAreValid(GroovyResolveResult[] candidates) {
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      LOG.assertTrue(element == null || element.isValid());
    }
  }

  private MethodResolverProcessor createMethodProcessor(boolean allVariants,
                                                        String name,
                                                        final boolean byShape,
                                                        @Nullable GrExpression upToArgument) {
    final PsiType[] argTypes = PsiUtil.getArgumentTypes(this, false, upToArgument, byShape);
    if (byShape && argTypes != null) {
      for (int i = 0; i < argTypes.length; i++) {
        argTypes[i] = TypeConversionUtil.erasure(argTypes[i]);
      }
    }
    return new MethodResolverProcessor(name, this, false, GrReferenceResolveUtil.getQualifierType(this), argTypes, getTypeArguments(),
                                       allVariants, byShape);
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
      if (TokenSets.STRING_LITERAL_SET.contains(nodeType)) {
        final Object value = GrLiteralImpl.getLiteralValue(nameElement);
        if (value instanceof String) {
          return (String)value;
        }
      }

      return nameElement.getText();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final GroovyResolveResult result = advancedResolve();
    if (result.isInvokedOnProperty()) {
      final String name = GroovyPropertyUtils.getPropertyNameByAccessorName(newElementName);
      if (name != null) {
        newElementName = name;
      }
    }
    if (PsiUtil.isThisOrSuperRef(this)) return this;

    return handleElementRenameSimple(newElementName);
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
    if (getKind() == Kind.TYPE_OR_PROPERTY && resolve() instanceof PsiPackage) return true;

    final GrExpression qualifier = getQualifier();
    if (!(qualifier instanceof GrReferenceExpressionImpl)) return false;
    return ((GrReferenceExpressionImpl)qualifier).isFullyQualified();
  }

  public PsiElement handleElementRenameSimple(String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      final PsiElement old = getReferenceNameElement();
      if (old == null) throw new IncorrectOperationException("ref has no name element");

      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteralForReference(newElementName);
      old.replace(element);
      return this;
    }

    return super.handleElementRenameSimple(newElementName);
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
    final InferenceContext context = TypeInferenceHelper.getCurrentContext();
    return context.getCachedValue(this, new Computable<GroovyResolveResult[]>() {
      @Override
      public GroovyResolveResult[] compute() {
        Pair<GrReferenceExpressionImpl, InferenceContext> key = Pair.create(GrReferenceExpressionImpl.this, context);
        GroovyResolveResult[] value = RecursionManager.doPreventingRecursion(key, true, new Computable<GroovyResolveResult[]>() {
          @Override
          public GroovyResolveResult[] compute() {
            return doPolyResolve(false, false);
          }
        });
        return value == null ? GroovyResolveResult.EMPTY_ARRAY : value;
      }
    });
  }

  private static final ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> POLY_RESOLVER = new ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl>() {
    @NotNull
    public GroovyResolveResult[] resolve(@NotNull GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      return refExpr.doPolyResolve(incompleteCode, true);
    }
  };
  private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

  public PsiType getNominalType() {
    final GroovyResolveResult resolveResult = advancedResolve();
    PsiElement resolved = resolveResult.getElement();

    for (GrReferenceTypeEnhancer enhancer : GrReferenceTypeEnhancer.EP_NAME.getExtensions()) {
      PsiType type = enhancer.getReferenceType(this, resolved);
      if (type != null) {
        return type;
      }
    }

    IElementType dotType = getDotTokenType();
    if (dotType == mMEMBER_POINTER) {
      return GrClosureType.create(multiResolve(false), this);
    }

    if (isDefinitelyKeyOfMap()) {
      final PsiType type = getTypeFromMapAccess(this);
      if (type != null) {
        return type;
      }
    }

    PsiType result = getNominalTypeInner(resolved);
    if (result == null) return null;

    result = TypesUtil.substituteBoxAndNormalizeType(result, resolveResult.getSubstitutor(), resolveResult.getSpreadState(), this);
    return result;
  }

  @Nullable
  private PsiType getNominalTypeInner(PsiElement resolved) {
    if (resolved == null && !"class".equals(getReferenceName())) {
      resolved = resolve();
    }

    if (resolved instanceof PsiClass) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      if (PsiUtil.isInstanceThisRef(this)) {
        final PsiClassType categoryType = GdkMethodUtil.getCategoryType((PsiClass)resolved);
        if (categoryType != null) {
          return categoryType;
        }
        else {
          return factory.createType((PsiClass)resolved);
        }
      }
      if (getParent() instanceof GrReferenceExpression || PsiUtil.isSuperReference(this)) {
        return factory.createType((PsiClass)resolved);
      }
      else {
        return TypesUtil.createJavaLangClassType(factory.createType((PsiClass)resolved), getProject(), getResolveScope());
      }
    }

    if (resolved instanceof GrVariable) {
      return ((GrVariable)resolved).getDeclaredType();
    }

    if (resolved instanceof PsiVariable) {
      return ((PsiVariable)resolved).getType();
    }

    if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolved;
      if (PropertyUtil.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
        return method.getParameterList().getParameters()[0].getType();
      }

      //'class' property with explicit generic
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null &&
          CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) &&
          "getClass".equals(method.getName())) {
        return TypesUtil.createJavaLangClassType(GrReferenceResolveUtil.getQualifierType(this), getProject(), getResolveScope());
      }

      return PsiUtil.getSmartReturnType(method);
    }

    if (resolved == null) {
      final PsiType fromClassRef = getTypeFromClassRef(this);
      if (fromClassRef != null) {
        return fromClassRef;
      }

      final PsiType fromMapAccess = getTypeFromMapAccess(this);
      if (fromMapAccess != null) {
        return fromMapAccess;
      }

      final PsiType fromSpreadOperator = getTypeFromSpreadOperator(this);
      if (fromSpreadOperator != null) {
        return fromSpreadOperator;
      }
    }

    return null;
  }

  @Nullable
  private static PsiType getTypeFromMapAccess(@NotNull GrReferenceExpressionImpl ref) {
    //map access
    GrExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) {
      PsiType qType = qualifier.getNominalType();
      if (qType instanceof PsiClassType) {
        PsiClassType.ClassResolveResult qResult = ((PsiClassType)qType).resolveGenerics();
        PsiClass clazz = qResult.getElement();
        if (clazz != null) {
          PsiClass mapClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(CommonClassNames.JAVA_UTIL_MAP, ref.getResolveScope());
          if (mapClass != null && mapClass.getTypeParameters().length == 2) {
            PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, qResult.getSubstitutor());
            if (substitutor != null) {
              PsiType substituted = substitutor.substitute(mapClass.getTypeParameters()[1]);
              if (substituted != null) {
                return PsiImplUtil.normalizeWildcardTypeByPosition(substituted, ref);
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiType getTypeFromSpreadOperator(@NotNull GrReferenceExpressionImpl ref) {
    if (ref.getDotTokenType() == GroovyTokenTypes.mSPREAD_DOT) {
      return TypesUtil.createType(CommonClassNames.JAVA_UTIL_LIST, ref);
    }

    return null;
  }

  @Nullable
  private static PsiType getTypeFromClassRef(@NotNull GrReferenceExpressionImpl ref) {
    if ("class".equals(ref.getReferenceName())) {
      return TypesUtil.createJavaLangClassType(GrReferenceResolveUtil.getQualifierType(ref), ref.getProject(), ref.getResolveScope());
    }
    return null;
  }

  private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    @Nullable
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      PsiType result = GrReassignedLocalVarsChecker.checkReassignedVar(refExpr, true);
      if (result != null) return result;

      if (GrUnresolvedAccessInspection.isClassReference(refExpr)) {
        GrExpression qualifier = refExpr.getQualifier();
        LOG.assertTrue(qualifier != null);
        return TypesUtil.createJavaLangClassType(qualifier.getType(), refExpr.getProject(), refExpr.getResolveScope());
      }

      final PsiElement resolved = refExpr.resolve();
      final PsiType inferred = getInferredTypes(refExpr, resolved);
      final PsiType nominal = refExpr.getNominalType();
      if (inferred == null || PsiType.NULL.equals(inferred)) {
        if (nominal == null) {
          //inside nested closure we could still try to infer from variable initializer. Not sound, but makes sense
          if (resolved instanceof GrVariable) {
            LOG.assertTrue(resolved.isValid());
            return ((GrVariable)resolved).getTypeGroovy();
          }
        }

        return nominal;
      }

      if (nominal == null) return inferred;
      if (!TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(nominal), inferred, false)) {
        if (resolved instanceof GrVariable && ((GrVariable)resolved).getTypeElementGroovy() != null) {
          return nominal;
        }
      }
      return inferred;
    }
  }

  @Nullable
  private static PsiType getInferredTypes(GrReferenceExpressionImpl refExpr, @Nullable PsiElement resolved) {
    final GrExpression qualifier = refExpr.getQualifier();
    if (qualifier == null && !(resolved instanceof PsiClass || resolved instanceof PsiPackage)) {
      return TypeInferenceHelper.getCurrentContext().getVariableType(refExpr);
    }
    else if (qualifier != null) {
      //map access
      PsiType qType = qualifier.getType();
      if (qType instanceof PsiClassType && !(qType instanceof GrMapType)) {
        final PsiType mapValueType = getTypeFromMapAccess(refExpr);
        if (mapValueType != null) {
          return mapValueType;
        }
      }
    }
    return null;
  }

  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  @NotNull
  private GroovyResolveResult[] doPolyResolve(boolean incompleteCode, boolean genericsMatter) {
    String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (incompleteCode) {
      ResolverProcessor processor = CompletionProcessor.createRefSameNameProcessor(this, name);
      GrReferenceResolveUtil.resolveImpl(processor, this);
      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0 && !PsiUtil.isSingleBindingVariant(propertyCandidates)) return propertyCandidates;
    }

    try {
      ResolveProfiler.start();
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
    finally {
      final long time = ResolveProfiler.finish();
      ResolveProfiler.write("ref " + getText() + " " + hashCode() + " : " + time);
    }
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
    PsiElement baseTarget = resolve();
    if (getManager().areElementsEquivalent(element, baseTarget)) {
      return true;
    }

    PsiElement target = GroovyTargetElementEvaluator.correctSearchTargets(baseTarget);
    if (target != baseTarget && getManager().areElementsEquivalent(element, target)) {
      return true;
    }

    if (element instanceof PsiMethod && target instanceof PsiMethod) {
      PsiMethod[] superMethods = ((PsiMethod)target).findSuperMethods(false);
      //noinspection SuspiciousMethodCalls
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
    ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, false, POLY_RESOLVER);
    return results.length == 1 ? (GroovyResolveResult)results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    final ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, incomplete, POLY_RESOLVER);
    return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
  }

  @Override
  public void processVariants(PrefixMatcher matcher, CompletionParameters parameters, Consumer<LookupElement> consumer) {
    CompleteReferenceExpression.processVariants(matcher, consumer, this, parameters);
  }

  @NotNull
  public GroovyResolveResult[] getSameNameVariants() {
    return doPolyResolve(true, true);
  }

  public GrReferenceExpression bindToElementViaStaticImport(@NotNull PsiMember member) {
    if (getQualifier() != null) {
      throw new IncorrectOperationException("Reference has qualifier");
    }

    if (StringUtil.isEmpty(getReferenceName())) {
      throw new IncorrectOperationException("Reference has empty name");
    }

    PsiClass containingClass = member.getContainingClass();
    if (containingClass == null) {
      throw new IncorrectOperationException("Member has no containing class");
    }
    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFile) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      String text = "import static " + containingClass.getQualifiedName() + "." + member.getName();
      final GrImportStatement statement = factory.createImportStatementFromText(text);
      ((GroovyFile)file).addImport(statement);
    }
    return this;
  }
}

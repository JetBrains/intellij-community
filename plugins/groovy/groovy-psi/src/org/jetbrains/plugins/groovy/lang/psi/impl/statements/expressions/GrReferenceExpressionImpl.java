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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

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
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;
import org.jetbrains.plugins.groovy.util.ResolveProfiler;

import java.util.*;

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

    final String qname = TypesUtil.getQualifiedName(type);
    if (qname != null) {
      if (qname.startsWith("java.")) return true; //so we have jdk map here
      if (GroovyCommonClassNames.GROOVY_UTIL_CONFIG_OBJECT.equals(qname)) return false;
      if (qname.startsWith("groovy.")) return true; //we have gdk map here
    }

    return false;
  }

  @NotNull
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
          if (containingClass.getLanguage() != GroovyLanguage.INSTANCE &&
              !InheritanceUtil.isInheritor(containingClass, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME)) {
            continue;
          }
        }
      }
      filtered.add(result);
    }

    return ContainerUtil.toArray(filtered, new GroovyResolveResult[filtered.size()]);
  }

  @NotNull
  private GroovyResolveResult[] resolveTypeOrPropertyInner() {
    PsiElement nameElement = getReferenceNameElement();
    String name = getReferenceName();

    if (name == null || nameElement == null) return GroovyResolveResult.EMPTY_ARRAY;

    IElementType nameType = nameElement.getNode().getElementType();
    if (nameType == GroovyTokenTypes.kTHIS) {
      GroovyResolveResult[] results = GrThisReferenceResolver.resolveThisExpression(this);
      if (results != null) {
        return results;
      }
    }
    else if (nameType == GroovyTokenTypes.kSUPER) {
      GroovyResolveResult[] results = GrSuperReferenceResolver.resolveSuperExpression(this);
      if (results != null) {
        return results;
      }
    }


    EnumSet<ClassHint.ResolveKind> kinds = getParent() instanceof GrReferenceExpression
                                           ? ClassHint.RESOLVE_KINDS_CLASS_PACKAGE
                                           : ClassHint.RESOLVE_KINDS_CLASS;

    GroovyResolveResult[] classCandidates = null;

    GrReferenceResolveRunner resolveRunner = new GrReferenceResolveRunner(this);

    ResolverProcessor processor = new PropertyResolverProcessor(name, this);
    resolveRunner.resolveImpl(processor);
    final GroovyResolveResult[] fieldCandidates = processor.getCandidates();

    if (hasAt()) {
      return fieldCandidates;
    }


    boolean canBeClassOrPackage = ResolveUtil.canBeClassOrPackage(this);

    if (canBeClassOrPackage && findClassOrPackageAtFirst()) {
      ResolverProcessor classProcessor = new ClassResolverProcessor(name, this, kinds);
      resolveRunner.resolveImpl(classProcessor);
      classCandidates = classProcessor.getCandidates();
      if (classCandidates.length > 0 && containsPackage(classCandidates)) return classCandidates;
    }

    //if reference expression is in class we need to return field instead of accessor method
    for (GroovyResolveResult candidate : fieldCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiUtil.getContextClass(this) == containingClass) return fieldCandidates;
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
        new AccessorResolverProcessor(accessorName, name, this, !isLValue, false, PsiImplUtil.getQualifierType(this), getTypeArguments());
      resolveRunner.resolveImpl(accessorResolver);
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
      resolveRunner.resolveImpl(classProcessor);
      classCandidates = classProcessor.getCandidates();
    }

    if (classCandidates != null && classCandidates.length > 0) return classCandidates;
    if (!accessorResults.isEmpty()) return new GroovyResolveResult[]{accessorResults.get(0)};
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static boolean containsPackage(@NotNull GroovyResolveResult[] candidates) {
    for (GroovyResolveResult candidate : candidates) {
      if (candidate.getElement() instanceof PsiPackage) return true;
    }
    return false;
  }

  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return resolveMethodOrProperty(true, upToArgument, true);
  }

  private void processMethods(@NotNull MethodResolverProcessor methodResolver) {
    new GrReferenceResolveRunner(this).resolveImpl(methodResolver);
    if (methodResolver.hasApplicableCandidates()) {
      return;
    }

    // Search in ClosureMissingMethodContributor
    if (!isQualified() && getContext() instanceof GrMethodCall) {
      ClosureMissingMethodContributor.processMethodsFromClosures(this, methodResolver);
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

    GrReferenceResolveRunner resolveRunner = new GrReferenceResolveRunner(this);

    PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(name, this);
    resolveRunner.resolveImpl(propertyResolver);
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
          put(ClassHint.RESOLVE_CONTEXT, result.getCurrentFileResolveContext()).
          put(SpreadState.SPREAD_STATE, result.getSpreadState());
        PsiElement element = result.getElement();
        assert element != null;
        methodResolver.execute(element, state);
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
        new AccessorResolverProcessor(getterName, name, this, true, genericsMatter, PsiImplUtil.getQualifierType(this), getTypeArguments());
      resolveRunner.resolveImpl(getterResolver);
      final GroovyResolveResult[] candidates = getterResolver.getCandidates(); //can be only one candidate
      if (!allVariants && candidates.length == 1) {
        return candidates;
      }
      ContainerUtil.addAll(allCandidates, candidates);
    }

    if (!allCandidates.isEmpty()) {
      return allCandidates.toArray(new GroovyResolveResult[allCandidates.size()]);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static void filterOutBindings(@NotNull List<GroovyResolveResult> candidates) {
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

  @NotNull
  private Pair<Boolean, GroovyResolveResult[]> resolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    if (allVariants) {
      return doResolveByShape(true, upToArgument);
    }

    LOG.assertTrue(upToArgument == null);

    return TypeInferenceHelper.getCurrentContext().getCachedValue(this, new NullableComputable<Pair<Boolean, GroovyResolveResult[]>>() {
      @Override
      public Pair<Boolean, GroovyResolveResult[]> compute() {
        return doResolveByShape(false, null);
      }
    });
  }

  @NotNull
  private Pair<Boolean, GroovyResolveResult[]> doResolveByShape(boolean allVariants, @Nullable GrExpression upToArgument) {
    final String name = getReferenceName();
    LOG.assertTrue(name != null);

    final MethodResolverProcessor shapeProcessor = createMethodProcessor(allVariants, name, true, upToArgument);
    processMethods(shapeProcessor);
    GroovyResolveResult[] candidates = shapeProcessor.getCandidates();
    assertAllAreValid(candidates);
    return Pair.create(shapeProcessor.hasApplicableCandidates(), candidates);
  }

  private static void assertAllAreValid(@NotNull GroovyResolveResult[] candidates) {
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      LOG.assertTrue(element == null || element.isValid());
    }
  }

  @NotNull
  private MethodResolverProcessor createMethodProcessor(boolean allVariants,
                                                        @Nullable String name,
                                                        final boolean byShape,
                                                        @Nullable GrExpression upToArgument) {
    final PsiType[] argTypes = PsiUtil.getArgumentTypes(this, false, upToArgument, byShape);
    if (byShape && argTypes != null) {
      for (int i = 0; i < argTypes.length; i++) {
        argTypes[i] = TypeConversionUtil.erasure(argTypes[i]);
      }
    }
    PsiType qualifierType = PsiImplUtil.getQualifierType(this);
    return new MethodResolverProcessor(name, this, false, qualifierType, argTypes, getTypeArguments(), allVariants, byShape);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Override
  @Nullable
  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    if (TokenSets.REFERENCE_NAMES.contains(lastChild.getElementType())) {
      return lastChild.getPsi();
    }

    return null;
  }

  @Override
  @NotNull
  public PsiReference getReference() {
    return this;
  }

  @Override
  @Nullable
  public GrExpression getQualifier() {
    return getQualifierExpression();
  }

  @Override
  @Nullable
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

  @Override
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
    GrReferenceExpression qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(qName);
    final GrTypeArgumentList list = getTypeArgumentList();
    if (list != null) {
      qualifiedRef.getNode().addChild(list.copy().getNode());
    }
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

  @Override
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

  @Override
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
    @Override
    @NotNull
    public GroovyResolveResult[] resolve(@NotNull GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      return refExpr.doPolyResolve(incompleteCode, true);
    }
  };
  private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

  @Override
  @Nullable
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
    if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
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

    result = TypesUtil.substituteAndNormalizeType(result, resolveResult.getSubstitutor(), resolveResult.getSpreadState(), this);
    return result;
  }

  @Nullable
  private PsiType getNominalTypeInner(@Nullable PsiElement resolved) {
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
      else if (PsiUtil.isSuperReference(this)) {
        PsiClass contextClass = PsiUtil.getContextClass(this);
        if (GrTraitUtil.isTrait(contextClass)) {
          PsiClassType[] extendsTypes = contextClass.getExtendsListTypes();
          PsiClassType[] implementsTypes = contextClass.getImplementsListTypes();

          PsiClassType[] superTypes = ArrayUtil.mergeArrays(implementsTypes, extendsTypes, PsiClassType.ARRAY_FACTORY);

          return PsiIntersectionType.createIntersection(ArrayUtil.reverseArray(superTypes));
        }
        return factory.createType((PsiClass)resolved);
      }
      if (getParent() instanceof GrReferenceExpression) {
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
        return TypesUtil.createJavaLangClassType(PsiImplUtil.getQualifierType(this), getProject(), getResolveScope());
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
      return TypesUtil.createJavaLangClassType(PsiImplUtil.getQualifierType(ref), ref.getProject(), ref.getResolveScope());
    }
    return null;
  }

  private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    @Override
    @Nullable
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      if (ResolveUtil.isClassReference(refExpr)) {
        GrExpression qualifier = refExpr.getQualifier();
        LOG.assertTrue(qualifier != null);
        return TypesUtil.createJavaLangClassType(qualifier.getType(), refExpr.getProject(), refExpr.getResolveScope());
      }

      if (PsiUtil.isCompileStatic(refExpr)) {
        final GroovyResolveResult resolveResult = refExpr.advancedResolve();
        final PsiElement resolvedF = resolveResult.getElement();
        final PsiType type;
        if (resolvedF instanceof GrField) {
          type = ((GrField)resolvedF).getType();
        }
        else if (resolvedF instanceof GrAccessorMethod) {
          type = ((GrAccessorMethod)resolvedF).getProperty().getType();
        }
        else {
          type = null;
        }
        if (type != null) {
          return resolveResult.getSubstitutor().substitute(type);
        }
      }

      final PsiElement resolved = refExpr.resolve();
      final PsiType nominal = refExpr.getNominalType();

      Boolean reassigned = GrReassignedLocalVarsChecker.isReassignedVar(refExpr);
      if (reassigned != null && reassigned.booleanValue()) {
        return GrReassignedLocalVarsChecker.getReassignedVarType(refExpr, true);
      }

      final PsiType inferred = getInferredTypes(refExpr, resolved);
      if (inferred == null) {
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
  private static PsiType getInferredTypes(@NotNull GrReferenceExpressionImpl refExpr, @Nullable PsiElement resolved) {
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

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  @Override
  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  @NotNull
  private GroovyResolveResult[] doPolyResolve(boolean incompleteCode, boolean genericsMatter) {
    String name = getReferenceName();
    if (name == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (incompleteCode) {
      ResolverProcessor processor = CompletionProcessor.createRefSameNameProcessor(this, name);
      new GrReferenceResolveRunner(this).resolveImpl(processor);
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

  @NotNull
  private Kind getKind() {
    if (getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER) return Kind.METHOD_OR_PROPERTY;

    PsiElement parent = getParent();
    if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
      return Kind.METHOD_OR_PROPERTY_OR_TYPE;
    }

    return Kind.TYPE_OR_PROPERTY;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public boolean hasAt() {
    return findChildByType(GroovyTokenTypes.mAT) != null;
  }

  @Override
  public boolean hasMemberPointer() {
    return findChildByType(GroovyTokenTypes.mMEMBER_POINTER) != null;
  }

  @Override
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

  @Override
  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }


  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  @Nullable
  public GrExpression getQualifierExpression() {
    return findExpressionChild(this);
  }

  @Override
  @Nullable
  public PsiElement getDotToken() {
    return findChildByType(TokenSets.DOTS);
  }

  @Override
  public void replaceDotToken(PsiElement newDot) {
    if (newDot == null) return;
    if (!TokenSets.DOTS.contains(newDot.getNode().getElementType())) return;
    final PsiElement oldDot = getDotToken();
    if (oldDot == null) return;

    getNode().replaceChild(oldDot.getNode(), newDot.getNode());
  }

  @Override
  @Nullable
  public IElementType getDotTokenType() {
    PsiElement dot = getDotToken();
    return dot == null ? null : dot.getNode().getElementType();
  }

  @Override
  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, false, POLY_RESOLVER);
    return results.length == 1 ? (GroovyResolveResult)results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @Override
  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    final ResolveResult[] results = TypeInferenceHelper.getCurrentContext().multiResolve(this, incomplete, POLY_RESOLVER);
    return results.length == 0 ? GroovyResolveResult.EMPTY_ARRAY : (GroovyResolveResult[])results;
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getSameNameVariants() {
    return doPolyResolve(true, true);
  }

  @Override
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

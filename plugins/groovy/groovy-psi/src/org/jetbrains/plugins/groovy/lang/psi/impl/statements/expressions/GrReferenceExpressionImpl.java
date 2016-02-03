/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache.PolyVariantResolver;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.profiling.ResolveProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrExpressionTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessorBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl<GrExpression> implements GrReferenceExpression {

  private static final Logger LOG = Logger.getInstance(GrReferenceExpressionImpl.class);

  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  private static List<GroovyResolveResult> filterMembersFromSuperClasses(GroovyResolveResult[] results) {
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
    return filtered;
  }

  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return _resolve(false, true, upToArgument);
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
    if (!ResolveUtil.canResolveToMethod(this) && resolve() instanceof PsiPackage) return true;

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
  public final PsiElement resolve() {
    return advancedResolve().getElement();
  }

  private static final PolyVariantResolver<GrReferenceExpressionImpl> POLY_RESOLVER = new PolyVariantResolver<GrReferenceExpressionImpl>() {
    @Override
    @NotNull
    public GroovyResolveResult[] resolve(@NotNull final GrReferenceExpressionImpl refExpr, final boolean incompleteCode) {
      return refExpr.doPolyResolve(incompleteCode);
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

    if (ResolveUtil.isDefinitelyKeyOfMap(this)) {
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
    if (qualifier instanceof GrReferenceExpression) {
      if (((GrReferenceExpression)qualifier).resolve() instanceof PsiClass) return null;
    }
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
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
      final PsiElement resolved = refExpr.resolve();

      for (GrExpressionTypeCalculator calculator : GrExpressionTypeCalculator.EP_NAME.getExtensions()) {
        PsiType type = calculator.calculateType(refExpr, resolved);
        if (type != null) return type;
      }

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
    if (!(resolved instanceof PsiClass) && !(resolved instanceof PsiPackage)) {
      if (qualifier == null) {
        return TypeInferenceHelper.getCurrentContext().getVariableType(refExpr);
      }
      else {
        //map access
        PsiType qType = qualifier.getType();
        if (qType instanceof PsiClassType && !(qType instanceof GrMapType)) {
          final PsiType mapValueType = getTypeFromMapAccess(refExpr);
          if (mapValueType != null) {
            return mapValueType;
          }
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
  private GroovyResolveResult[] doPolyResolve(@SuppressWarnings("UnusedParameters") boolean incompleteCode) {
    final PsiElement nameElement = getReferenceNameElement();
    final String name = getReferenceName();
    if (name == null || nameElement == null) return GroovyResolveResult.EMPTY_ARRAY;

    try {
      ResolveProfiler.start();
      boolean canBeMethod = ResolveUtil.canResolveToMethod(this);
      if (!canBeMethod) {
        if (ResolveUtil.isDefinitelyKeyOfMap(this)) return GroovyResolveResult.EMPTY_ARRAY;
        final IElementType nameType = nameElement.getNode().getElementType();
        if (nameType == GroovyTokenTypes.kTHIS) {
          final GroovyResolveResult[] results = GrThisReferenceResolver.resolveThisExpression(this);
          if (results != null) return results;
        }
        else if (nameType == GroovyTokenTypes.kSUPER) {
          final GroovyResolveResult[] results = GrSuperReferenceResolver.resolveSuperExpression(this);
          if (results != null) return results;
        }
      }

      final GroovyResolveResult[] results = _resolve(incompleteCode, false, null);
      if (results.length == 0) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }
      else if (!canBeMethod) {
        if (!ResolveUtil.mayBeKeyOfMap(this)) {
          return results;
        }
        else {
          //filter out all members from super classes. We should return only accessible members from map classes
          final List<GroovyResolveResult> filtered = filterMembersFromSuperClasses(results);
          return ContainerUtil.toArray(filtered, new GroovyResolveResult[filtered.size()]);
        }
      }
      else {
        return results;
      }
    }
    finally {
      final long time = ResolveProfiler.finish();
      ResolveProfiler.write("ref", this, time);
    }
  }

  @NotNull
  private GroovyResolveResult[] _resolve(boolean incomplete, boolean allVariants, @Nullable GrExpression upToArgument) {
    final GroovyResolverProcessor processor = GroovyResolverProcessorBuilder.builder()
      .setIncomplete(incomplete)
      .setAllVariants(allVariants)
      .setUpToArgument(upToArgument)
      .build(this);
    new GrReferenceResolveRunner(this, processor).resolveImpl();
    return processor.getCandidatesArray();
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
    return doPolyResolve(true);
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

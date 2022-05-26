// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyTargetElementEvaluator;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrStaticExpressionReference;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

import java.util.*;

import static com.intellij.psi.util.PsiUtilCore.ensureValid;
import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.REFERENCE_DOTS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.isLValue;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.isRValue;
import static org.jetbrains.plugins.groovy.lang.resolve.impl.IncompleteKt.resolveIncomplete;
import static org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculatorKt.getTypeFromCandidate;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl<GrExpression> implements GrReferenceExpression {
  private static final Logger LOG = Logger.getInstance(GrReferenceExpressionImpl.class);

  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  private final GroovyReference myStaticReference = new GrStaticExpressionReference(this);
  private final GroovyReference myRValueReference = new GrRValueExpressionReference(this);
  private final GroovyReference myLValueReference = new GrLValueExpressionReference(this);

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Override
  @Nullable
  public PsiElement getReferenceNameElement() {
    return findChildByType(TokenSets.REFERENCE_NAMES);
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
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      final PsiElement old = getReferenceNameElement();
      if (old == null) throw new IncorrectOperationException("ref has no name element");

      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteralForReference(newElementName);
      old.replace(element);
      return this;
    }

    if (PsiUtil.isThisOrSuperRef(this)) return this;

    final GroovyResolveResult result = advancedResolve();
    if (result.isInvokedOnProperty()) {
      final String name = GroovyPropertyUtils.getPropertyNameByAccessorName(newElementName);
      if (name != null) {
        newElementName = name;
      }
    }

    return super.handleElementRename(newElementName);
  }

  @NotNull
  @Override
  protected GrReferenceElement<GrExpression> createQualifiedRef(@NotNull String qName) {
    return GroovyPsiElementFactory.getInstance(getProject()).createReferenceExpressionFromText(qName);
  }

  @Override
  public boolean isFullyQualified() {
    if (!hasMemberPointer() && !ResolveUtil.canResolveToMethod(this) && resolve() instanceof PsiPackage) return true;

    final GrExpression qualifier = getQualifier();
    if (!(qualifier instanceof GrReferenceExpressionImpl)) return false;
    return ((GrReferenceExpressionImpl)qualifier).isFullyQualified();
  }

  @Override
  public String toString() {
    return "Reference expression";
  }

  @Override
  @Nullable
  public PsiType getNominalType() {
    return getNominalType(false);
  }

  @Nullable
  private PsiType getNominalType(boolean rValue) {
    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(lrResolve(rValue));
    PsiElement resolved = resolveResult.getElement();

    for (GrReferenceTypeEnhancer enhancer : GrReferenceTypeEnhancer.EP_NAME.getExtensions()) {
      PsiType type = enhancer.getReferenceType(this, resolved);
      if (type != null) {
        return type;
      }
    }

    PsiType result = getNominalTypeInner(resolveResult);
    if (result == null) return null;

    result = TypesUtil.substituteAndNormalizeType(result, resolveResult.getSubstitutor(), resolveResult.getSpreadState(), this);
    return result;
  }

  @Nullable
  private PsiType getNominalTypeInner(GroovyResolveResult result) {
    PsiElement resolved = result.getElement();
    if (resolved instanceof GroovyProperty) {
      return ((GroovyProperty)resolved).getPropertyType();
    }

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

          if (superTypes.length > 0) {
            return PsiIntersectionType.createIntersection(ArrayUtil.reverseArray(superTypes));
          }
        }
        return factory.createType((PsiClass)resolved);
      }
      return TypesUtil.createJavaLangClassType(factory.createType((PsiClass)resolved), this);
    }

    if (resolved instanceof GrVariable) {
      return ((GrVariable)resolved).getDeclaredType();
    }

    if (resolved instanceof PsiVariable) {
      return ((PsiVariable)resolved).getType();
    }

    if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolved;
      if (PropertyUtilBase.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
        return method.getParameterList().getParameters()[0].getType();
      }

      if (result instanceof GroovyMethodResult) {
        return getTypeFromCandidate((GroovyMethodResult)result, this);
      }

      return PsiUtil.getSmartReturnType(method);
    }

    if (resolved == null) {
      if ("class".equals(getReferenceName())) {
        final PsiType fromClassRef = getTypeFromClassRef();
        if (fromClassRef != null) {
          return fromClassRef;
        }
      }

      final PsiType fromSpreadOperator = getTypeFromSpreadOperator(this);
      if (fromSpreadOperator != null) {
        return fromSpreadOperator;
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
  private PsiType getTypeFromClassRef() {
    PsiType qualifierType = PsiImplUtil.getQualifierType(this);

    if (qualifierType == null && !CompileStaticUtil.isCompileStatic(this)) return null;
    return TypesUtil.createJavaLangClassType(qualifierType, this);
  }

  @Nullable
  private static PsiType calculateType(@NotNull GrReferenceExpressionImpl refExpr) {
    final Collection<? extends GroovyResolveResult> results = refExpr.lrResolve(true);
    final GroovyResolveResult result = PsiImplUtil.extractUniqueResult(results);
    final PsiElement resolved = result.getElement();

    PsiType typeFromCalculators = GrTypeCalculator.getTypeFromCalculators(refExpr);
    if (typeFromCalculators != null) return typeFromCalculators;

    if (ResolveUtil.isClassReference(refExpr)) {
      GrExpression qualifier = refExpr.getQualifier();
      LOG.assertTrue(qualifier != null);
      return qualifier.getType();
    }

    final PsiType nominal = refExpr.getNominalType(true);
    final PsiType inferred = getInferredTypes(refExpr, resolved);
    if (inferred == null) {
      return nominal == null ? getDefaultType(refExpr, result) : nominal;
    }

    if (nominal == null) {
      if (inferred.equals(PsiType.NULL) && CompileStaticUtil.isCompileStatic(refExpr)) {
        return TypesUtil.getJavaLangObject(refExpr);
      }
      else {
        return inferred;
      }
    }
    if (!TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(nominal), inferred, false)) {
      if (resolved instanceof GrVariable) {
        if (((GrVariable)resolved).getTypeElementGroovy() != null) {
          return nominal;
        }
      }
      else if (resolved instanceof PsiVariable) {
        return nominal;
      }
    }
    return inferred;
  }

  @Nullable
  private static PsiType getInferredTypes(@NotNull GrReferenceExpressionImpl refExpr, @Nullable PsiElement resolved) {
    final GrExpression qualifier = refExpr.getQualifier();
    if (qualifier != null || resolved instanceof PsiClass || resolved instanceof PsiPackage || resolved instanceof PsiEnumConstant) {
      return null;
    }
    return TypeInferenceHelper.getCurrentContext().getVariableType(refExpr);
  }

  @Nullable
  private static PsiType getDefaultType(@NotNull GrReferenceExpression refExpr, @NotNull GroovyResolveResult result) {
    final PsiElement resolved = result.getElement();
    if (resolved instanceof GrField) {
      ensureValid(resolved);
      if (CompileStaticUtil.isCompileStatic(refExpr)) {
        return TypesUtil.getJavaLangObject(refExpr);
      }
      else {
        return SpreadState.apply(((GrVariable)resolved).getTypeGroovy(), result.getSpreadState(), refExpr.getProject());
      }
    }
    else if (resolved instanceof GrVariable) {
      ensureValid(resolved);
      PsiType typeGroovy = SpreadState.apply(((GrVariable)resolved).getTypeGroovy(), result.getSpreadState(), refExpr.getProject());
      if (typeGroovy == null && CompileStaticUtil.isCompileStatic(refExpr)) {
        return TypesUtil.getJavaLangObject(refExpr);
      }
      else {
        return typeGroovy;
      }
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, e -> calculateType(e));
  }

  @Override
  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public boolean hasAt() {
    return false;
  }

  @Override
  public boolean hasMemberPointer() {
    return false;
  }

  @NotNull
  @Override
  public GroovyReference getStaticReference() {
    return myStaticReference;
  }

  @Nullable
  private GroovyReference getCallReference() {
    PsiElement parent = getParent();
    return parent instanceof GrMethodCall ? ((GrMethodCall)parent).getExplicitCallReference() : null;
  }

  @Nullable
  @Override
  public GroovyReference getRValueReference() {
    return isRValue(this) ? myRValueReference : null;
  }

  @Nullable
  @Override
  public GroovyReference getLValueReference() {
    return isLValue(this) ? myLValueReference : null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    GroovyResolveResult[] results = multiResolve(false);

    for (GroovyResolveResult result : results) {
      PsiElement baseTarget = result.getElement();
      if (baseTarget == null) continue;
      if (getManager().areElementsEquivalent(element, baseTarget)) {
        return true;
      }

      PsiElement target = GroovyTargetElementEvaluator.correctSearchTargets(baseTarget);
      if (target != baseTarget && getManager().areElementsEquivalent(element, target)) {
        return true;
      }

      if (element instanceof PsiMethod && target instanceof PsiMethod) {
        PsiMethod[] superMethods = ((PsiMethod)target).findSuperMethods(false);
        if (Arrays.asList(superMethods).contains(element)) {
          return true;
        }
      }
    }

    return false;
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
    return findChildByType(REFERENCE_DOTS);
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
  public PsiReference getReference() {
    return this;
  }

  private static final GroovyResolver<GrReferenceExpressionImpl> RESOLVER = new DependentResolver<>() {
    @Nullable
    @Override
    public Collection<PsiPolyVariantReference> collectDependencies(@NotNull GrReferenceExpressionImpl expression) {
      final GrExpression qualifier = expression.getQualifier();
      if (qualifier == null) return null;

      final List<PsiPolyVariantReference> result = new SmartList<>();
      qualifier.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof GrReferenceExpression) {
            super.visitElement(element);
          }
          else if (element instanceof GrMethodCall) {
            super.visitElement(((GrMethodCall)element).getInvokedExpression());
          }
          else if (element instanceof GrParenthesizedExpression) {
            GrExpression operand = ((GrParenthesizedExpression)element).getOperand();
            if (operand != null) super.visitElement(operand);
          }
        }

        @Override
        protected void elementFinished(PsiElement element) {
          if (element instanceof GrReferenceExpression) {
            result.add(((GrReferenceExpression)element));
          }
        }
      });
      return result;
    }

    @NotNull
    @Override
    public GroovyResolveResult[] doResolve(@NotNull GrReferenceExpressionImpl ref, boolean incomplete) {
      if (incomplete) {
        return resolveIncomplete(ref).toArray(GroovyResolveResult.EMPTY_ARRAY);
      }
      final GroovyReference rValueRef = ref.getRValueReference();
      final GroovyReference lValueRef = ref.getLValueReference();
      if (rValueRef != null && lValueRef != null) {
        // merge results from both references
        final Map<PsiElement, GroovyResolveResult> results = new HashMap<>();
        for (GroovyResolveResult result : rValueRef.resolve(false)) {
          results.putIfAbsent(result.getElement(), result);
        }
        for (GroovyResolveResult result : lValueRef.resolve(false)) {
          results.putIfAbsent(result.getElement(), result);
        }
        return results.values().toArray(GroovyResolveResult.EMPTY_ARRAY);
      }
      else if (rValueRef != null) {
        // r-value only
        return rValueRef.resolve(false).toArray(GroovyResolveResult.EMPTY_ARRAY);
      }
      else if (lValueRef != null) {
        // l-value only
        return lValueRef.resolve(false).toArray(GroovyResolveResult.EMPTY_ARRAY);
      }
      else {
        LOG.error("Reference expression has no references");
        return GroovyResolveResult.EMPTY_ARRAY;
      }
    }
  };

  @NotNull
  @Override
  public Collection<? extends GroovyResolveResult> resolve(boolean incomplete) {
    final PsiElement parent = getParent();
    if (parent instanceof GrConstructorInvocation) {
      // Archaeology notice.
      //
      // GrConstructorInvocation only consists of 'this'/'super' keyword and argument list.
      // It has own fake reference, while this GrReferenceExpression exists so user may click on something,
      // i.e. this GrReferenceExpression provides text range for GrConstructorInvocation.
      //
      // GrConstructorInvocation might have had its own real reference with proper range,
      // but instead it returns this GrReferenceExpression as invoked one.
      return ((GrConstructorInvocation)parent).getConstructorReference().resolve(incomplete);
    }

    final Collection<? extends GroovyResolveResult> staticResults = getStaticReference().resolve(false);
    if (!staticResults.isEmpty()) {
      return staticResults;
    }

    final GroovyReference callReference = getCallReference();
    if (callReference != null) {
      return callReference.resolve(incomplete);
    }

    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, RESOLVER);
  }

  @NotNull
  protected Collection<? extends GroovyResolveResult> lrResolve(boolean rValue) {
    final Collection<? extends GroovyResolveResult> staticResults = getStaticReference().resolve(false);
    if (!staticResults.isEmpty()) {
      return staticResults;
    }

    final GroovyReference callReference = getCallReference();
    if (callReference != null) {
      return callReference.resolve(false);
    }

    final GroovyReference ref = rValue ? getRValueReference() : getLValueReference();
    return ref == null ? emptyList() : ref.resolve(false);
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

  @Override
  public boolean isImplicitCallReceiver() {
    // `a.&foo()` compiles into `new MethodClosure(a, "foo").call()` as if `call` was explicitly in the code
    // `a.@foo()` compiles into `a@.foo.call()` as if `call` was an explicitly in the code
    return hasAt() || hasMemberPointer() || myStaticReference.resolve() instanceof GrVariable;
  }
}

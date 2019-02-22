// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.findUsages.MissingMethodAndPropertyUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.EmptyGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrScopeProcessorWithHints;
import org.jetbrains.plugins.groovy.transformations.impl.GroovyObjectTransformationSupport;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.ReferenceFixesKt.*;

public class GrUnresolvedAccessChecker {
  public static final Logger LOG = Logger.getInstance(GrUnresolvedAccessChecker.class);

  private static final LightCacheKey<Map<String, Boolean>> GROOVY_OBJECT_METHODS_CACHE = new LightCacheKey<Map<String, Boolean>>() {
    @Override
    protected long getModificationCount(PsiElement holder) {
      return holder.getManager().getModificationTracker().getModificationCount();
    }
  };


  private final HighlightDisplayKey myDisplayKey;
  private final boolean myInspectionEnabled;
  private final GrUnresolvedAccessInspection myInspection;

  public GrUnresolvedAccessChecker(@NotNull GroovyFileBase file, @NotNull Project project) {
    myInspectionEnabled = GrUnresolvedAccessInspection.isInspectionEnabled(file, project);
    myInspection = myInspectionEnabled ? GrUnresolvedAccessInspection.getInstance(file, project) : null;
    myDisplayKey = GrUnresolvedAccessInspection.findDisplayKey();
  }

  @Nullable
  public List<HighlightInfo> checkReferenceExpression(GrReferenceExpression ref) {
    List<HighlightInfo> info = checkRefInner(ref);
    addEmptyIntentionIfNeeded(ContainerUtil.getFirstItem(info));
    return info;
  }

  @Nullable
  private List<HighlightInfo> checkRefInner(GrReferenceExpression ref) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    if (refNameElement == null) return null;

    boolean inStaticContext = PsiUtil.isCompileStatic(ref) || GrStaticChecker.isPropertyAccessInStaticMethod(ref);
    GroovyResolveResult resolveResult = getBestResolveResult(ref);

    if (resolveResult.getElement() != null) {
      if (!myInspectionEnabled) return null;

      if (!isStaticOk(resolveResult)) {
        String message = GroovyBundle.message("cannot.reference.non.static", ref.getReferenceName());
        return Collections.singletonList(createAnnotationForRef(ref, inStaticContext, message));
      }

      return null;
    }

    if (ResolveUtil.isKeyOfMap(ref) || ResolveUtil.isClassReference(ref)) {
      return null;
    }

    if (!inStaticContext) {
      if (!myInspectionEnabled) return null;
      assert myInspection != null;

      if (!myInspection.myHighlightIfGroovyObjectOverridden && areGroovyObjectMethodsOverridden(ref)) return null;
      if (!myInspection.myHighlightIfMissingMethodsDeclared && areMissingMethodsDeclared(ref)) return null;

      if (GrUnresolvedAccessInspection.isSuppressed(ref)) return null;
    }

    if (inStaticContext || shouldHighlightAsUnresolved(ref)) {
      HighlightInfo info = createAnnotationForRef(ref, inStaticContext, GroovyBundle.message("cannot.resolve", ref.getReferenceName()));
      if (info == null) return null;

      ArrayList<HighlightInfo> result = ContainerUtil.newArrayList();
      result.add(info);
      if (ref.getParent() instanceof GrMethodCall) {
        ContainerUtil.addIfNotNull(result, registerStaticImportFix(ref, myDisplayKey));
      }
      else {
        registerCreateClassByTypeFix(ref, info, myDisplayKey);
        registerAddImportFixes(ref, info, myDisplayKey);
      }

      registerReferenceFixes(ref, info, myDisplayKey);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarAdapter(info, myDisplayKey));
      QuickFixFactory.getInstance().registerOrderEntryFixes(new QuickFixActionRegistrarAdapter(info, myDisplayKey), ref);
      return result;
    }

    return null;
  }

  private static boolean areMissingMethodsDeclared(GrReferenceExpression ref) {
    PsiType qualifierType = PsiImplUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) return false;

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) return false;

    if (ref.getParent() instanceof GrCall) {
      PsiMethod[] found = resolved.findMethodsByName("methodMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isMethodMissing(method)) return true;
      }
    }
    else {
      PsiMethod[] found = resolved.findMethodsByName("propertyMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isPropertyMissing(method)) return true;
      }
    }

    return false;
  }

  private static boolean areGroovyObjectMethodsOverridden(GrReferenceExpression ref) {
    PsiMethod patternMethod = findPatternMethod(ref);
    if (patternMethod == null) return false;

    GrExpression qualifier = ref.getQualifier();
    if (qualifier != null) {
      return checkGroovyObjectMethodsByQualifier(ref, patternMethod);
    }
    else {
      return checkMethodInPlace(ref, patternMethod);
    }
  }

  private static boolean checkMethodInPlace(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiElement container = PsiTreeUtil.getParentOfType(ref, GrClosableBlock.class, PsiMember.class, PsiFile.class);
    assert container != null;
    return checkContainer(patternMethod, container);
  }

  private static boolean checkContainer(@NotNull final PsiMethod patternMethod, @NotNull PsiElement container) {
    final String name = patternMethod.getName();

    Map<String, Boolean> cached = GROOVY_OBJECT_METHODS_CACHE.getCachedValue(container);
    if (cached == null) {
      GROOVY_OBJECT_METHODS_CACHE.putCachedValue(container, cached = ContainerUtil.newConcurrentMap());
    }

    Boolean cachedResult = cached.get(name);
    if (cachedResult != null) {
      return cachedResult.booleanValue();
    }

    boolean result = doCheckContainer(patternMethod, container, name);
    cached.put(name, result);

    return result;
  }

  private static boolean doCheckContainer(final PsiMethod patternMethod, PsiElement container, final String name) {
    final Ref<Boolean> result = new Ref<>(false);
    PsiScopeProcessor processor = new GrScopeProcessorWithHints(name, ClassHint.RESOLVE_KINDS_METHOD) {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiMethod &&
            name.equals(((PsiMethod)element).getName()) &&
            patternMethod.getParameterList().getParametersCount() == ((PsiMethod)element).getParameterList().getParametersCount() &&
            isNotFromGroovyObject((PsiMethod)element)) {
          result.set(true);
          return false;
        }
        return true;
      }
    };
    ResolveUtil.treeWalkUp(container, processor, true);
    return result.get();
  }

  private static boolean checkGroovyObjectMethodsByQualifier(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiType qualifierType = PsiImplUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) return false;

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) return false;

    PsiMethod found = resolved.findMethodBySignature(patternMethod, true);
    if (found == null) return false;

    return isNotFromGroovyObject(found);
  }

  private static boolean isNotFromGroovyObject(@NotNull PsiMethod found) {
    if (GroovyObjectTransformationSupport.isGroovyObjectSupportMethod(found)) return false;
    PsiClass aClass = found.getContainingClass();
    if (aClass == null) return false;
    String qname = aClass.getQualifiedName();
    if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) return false;
    if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) return false;
    return true;
  }

  @Nullable
  private static PsiMethod findPatternMethod(@NotNull GrReferenceExpression ref) {
    PsiClass groovyObject = JavaPsiFacade.getInstance(ref.getProject()).findClass(GroovyCommonClassNames.GROOVY_OBJECT,
                                                                                              ref.getResolveScope());
    if (groovyObject == null) return null;

    String methodName = ref.getParent() instanceof GrCall ? "invokeMethod"
                        : PsiUtil.isLValue(ref)           ? "setProperty"
                                                          : "getProperty";

    PsiMethod[] patternMethods = groovyObject.findMethodsByName(methodName, false);
    if (patternMethods.length != 1) return null;
    return patternMethods[0];
  }

  private void addEmptyIntentionIfNeeded(@Nullable HighlightInfo info) {
    if (info != null) {
      int s1 = info.quickFixActionMarkers != null ? info.quickFixActionMarkers.size() : 0;
      int s2 = info.quickFixActionRanges != null ? info.quickFixActionRanges.size() : 0;

      if (s1 + s2 == 0) {
        EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction(GrUnresolvedAccessInspection.getDisplayText());
        QuickFixAction.registerQuickFixAction(info, emptyIntentionAction, myDisplayKey);
      }
    }
  }

  private static boolean isStaticOk(GroovyResolveResult resolveResult) {
    if (resolveResult.isStaticsOK()) return true;

    PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved != null);
    LOG.assertTrue(resolved instanceof PsiModifierListOwner, resolved + " : " + resolved.getText());

    return ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC);
  }

  @NotNull
  private static GroovyResolveResult getBestResolveResult(GrReferenceExpression ref) {
    GroovyResolveResult[] results = ref.multiResolve(false);
    if (results.length == 0) return EmptyGroovyResolveResult.INSTANCE;
    if (results.length == 1) return results[0];

    for (GroovyResolveResult result : results) {
      if (result.isAccessible() && result.isStaticsOK()) return result;
    }

    for (GroovyResolveResult result : results) {
      if (result.isStaticsOK()) return result;
    }

    return results[0];
  }

  @Nullable
  private static HighlightInfo createAnnotationForRef(@NotNull GrReferenceElement ref,
                                                      boolean strongError,
                                                      @NotNull String message) {
    HighlightDisplayLevel displayLevel = strongError ? HighlightDisplayLevel.ERROR : GrUnresolvedAccessInspection.getHighlightDisplayLevel(ref.getProject(), ref);
    return GrInspectionUtil.createAnnotationForRef(ref, displayLevel, message);
  }

  @Nullable
  private static HighlightInfo registerStaticImportFix(@NotNull GrReferenceExpression referenceExpression,
                                                       @Nullable final HighlightDisplayKey key) {
    final String referenceName = referenceExpression.getReferenceName();
    if (StringUtil.isEmpty(referenceName)) return null;
    if (referenceExpression.getQualifier() != null) return null;

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(
      referenceExpression.getParent()).createUnconditionally();
    QuickFixAction.registerQuickFixAction(info, GroovyQuickFixFactory.getInstance().createGroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()), key);
    return info;
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr,
                                             HighlightInfo info,
                                             final HighlightDisplayKey key) {
    for (IntentionAction fix : generateReferenceExpressionFixes(refExpr)) {
      QuickFixAction.registerQuickFixAction(info, fix, key);
    }
  }

  private static void registerAddImportFixes(GrReferenceElement refElement, @Nullable HighlightInfo info, final HighlightDisplayKey key) {
    for (IntentionAction action : generateAddImportActions(refElement)) {
      QuickFixAction.registerQuickFixAction(info, action, key);
    }
  }

  private static void registerCreateClassByTypeFix(@NotNull GrReferenceElement refElement,
                                                   @Nullable HighlightInfo info,
                                                   HighlightDisplayKey key) {
    for (IntentionAction fix : generateCreateClassActions(refElement)) {
      QuickFixAction.registerQuickFixAction(info, fix, key);
    }
  }

  private static boolean shouldHighlightAsUnresolved(@NotNull GrReferenceExpression referenceExpression) {
    if (GrHighlightUtil.isDeclarationAssignment(referenceExpression)) return false;

    GrExpression qualifier = referenceExpression.getQualifier();
    if (qualifier != null && qualifier.getType() == null && !isRefToPackage(qualifier)) return false;

    if (qualifier != null &&
        referenceExpression.getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER &&
        referenceExpression.multiResolve(false).length > 0) {
      return false;
    }

    if (!GroovyUnresolvedHighlightFilter.shouldHighlight(referenceExpression)) return false;

    CollectConsumer<PomTarget> consumer = new CollectConsumer<>();
    for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
      searcher.findDeclarationsAt(referenceExpression, 0, consumer);
      if (!consumer.getResult().isEmpty()) return false;
    }

    return true;
  }

  private static boolean isRefToPackage(GrExpression expr) {
    return expr instanceof GrReferenceExpression && ((GrReferenceExpression)expr).resolve() instanceof PsiPackage;
  }
}

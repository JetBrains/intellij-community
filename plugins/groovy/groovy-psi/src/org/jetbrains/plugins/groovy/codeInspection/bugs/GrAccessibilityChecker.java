// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;

import java.util.ArrayList;
import java.util.List;

public class GrAccessibilityChecker {
  private static final Logger LOG = Logger.getInstance(GrAccessibilityChecker.class);

  private final HighlightDisplayKey myDisplayKey;
  private final boolean myInspectionEnabled;

  public GrAccessibilityChecker(@NotNull GroovyFileBase file, @NotNull Project project) {
    myInspectionEnabled = GroovyAccessibilityInspection.isInspectionEnabled(file, project);
    myDisplayKey = GroovyAccessibilityInspection.findDisplayKey();
  }

  @SuppressWarnings("MagicConstant")
  static GroovyFix[] buildFixes(PsiElement location, GroovyResolveResult resolveResult) {
    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMember)) return GroovyFix.EMPTY_ARRAY;
    final PsiMember refElement = (PsiMember)element;

    if (refElement instanceof PsiCompiledElement) return GroovyFix.EMPTY_ARRAY;

    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return GroovyFix.EMPTY_ARRAY;

    List<GroovyFix> fixes = new ArrayList<>();
    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      assert modifierListCopy != null;
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      String minModifier = PsiModifier.PROTECTED;
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {PsiModifier.PROTECTED, PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL};
      PsiClass accessObjectClass = PsiTreeUtil.getParentOfType(location, PsiClass.class, false);
      if (accessObjectClass == null) {
        final PsiFile file = location.getContainingFile();
        if (!(file instanceof GroovyFile)) return GroovyFix.EMPTY_ARRAY;
        accessObjectClass = ((GroovyFile)file).getScriptClass();
      }
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, location, accessObjectClass, null)) {
          fixes.add(new GrModifierFix(refElement, modifier, true, true, GrModifierFix.MODIFIER_LIST_OWNER));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return fixes.toArray(GroovyFix.EMPTY_ARRAY);
  }

  @Nullable
  public HighlightInfo checkCodeReferenceElement(@NotNull GrCodeReferenceElement ref) {
    HighlightInfo.Builder builder = checkReferenceImpl(ref);
    return builder==null?null:builder.create();
  }

  private HighlightInfo.Builder checkReferenceImpl(@NotNull GrReferenceElement<?> ref) {
    boolean isCompileStatic = CompileStaticUtil.isCompileStatic(ref);

    if (!needToCheck(ref, isCompileStatic)) return null;

    PsiElement parent = ref.getParent();
    if (parent instanceof GrConstructorCall) {
      Pair<@DetailedDescription String, GroovyResolveResult> constructorError = checkConstructorCall((GrConstructorCall)parent);
      if (constructorError != null) {
        Pair<HighlightInfo.Builder,HighlightSeverity> info = createAnnotationForRef(ref, isCompileStatic, constructorError.first);
        registerFixes(ref, constructorError.second, info.getFirst(), info.getSecond());
        return info.getFirst();
      }
    }

    GroovyResolveResult[] results = ref.multiResolve(false);
    boolean hasInaccessibleResults = ContainerUtil.or(results, GrAccessibilityChecker::checkResolveResult);
    if (hasInaccessibleResults) {
      String error = GroovyBundle.message("cannot.access", ref.getReferenceName());
      Pair<HighlightInfo.Builder,HighlightSeverity> info = createAnnotationForRef(ref, isCompileStatic, error);
      if (results.length == 1) {
        registerFixes(ref, results[0], info.getFirst(), info.getSecond());
      }
      return info.getFirst();
    }

    return null;
  }

  private void registerFixes(GrReferenceElement<?> ref, GroovyResolveResult result, @NotNull HighlightInfo.Builder info,
                             @NotNull HighlightSeverity severity) {
    PsiElement element = result.getElement();
    assert element != null;
    if (element instanceof LightElement) return;
    GroovyFix[] fixes = buildFixes(ref, result);
    if (fixes.length == 0) {
      String displayName = HighlightDisplayKey.getDisplayNameByKey(myDisplayKey);
      if (displayName != null) {
        IntentionAction action = new EmptyIntentionAction(displayName);
        info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDisplayKey), null, myDisplayKey);
      }
    }
    else {
      ProblemDescriptor descriptor = InspectionManager.getInstance(ref.getProject()).
        createProblemDescriptor(element, element, "", HighlightInfo.convertSeverityToProblemHighlight(severity), true,
                                LocalQuickFix.EMPTY_ARRAY);
      for (GroovyFix fix : fixes) {
        info.registerFix(new LocalQuickFixAsIntentionAdapter(fix, descriptor), null, HighlightDisplayKey.getDisplayNameByKey(myDisplayKey),
                         null,
                         myDisplayKey);
      }
    }
  }

  @Nullable
  public HighlightInfo checkReferenceExpression(@NotNull GrReferenceExpression ref) {
    HighlightInfo.Builder builder = checkReferenceImpl(ref);
    return builder==null?null:builder.create();
  }

  private static boolean checkResolveResult(GroovyResolveResult result) {
    return result != null &&
           result.getElement() != null &&
           !result.isAccessible();
  }

  private boolean needToCheck(GrReferenceElement<?> ref, boolean isCompileStatic) {
    if (isCompileStatic) return true;
    if (!myInspectionEnabled) return false;
    if (GroovyAccessibilityInspection.isSuppressed(ref)) return false;

    return true;
  }

  private static Pair<@DetailedDescription String, GroovyResolveResult> checkConstructorCall(GrConstructorCall constructorCall) {
    GroovyResolveResult result = constructorCall.advancedResolve();
    PsiElement resultElement = result.getElement();
    if (!(resultElement instanceof PsiMethod)) {
      return null;
    }
    if (checkResolveResult(result)) {
      return new Pair<>(GroovyBundle.message("cannot.access", PsiFormatUtil.formatMethod((PsiMethod)resultElement, PsiSubstitutor.EMPTY,
                                                                                         PsiFormatUtilBase.SHOW_NAME |
                                                                                         PsiFormatUtilBase.SHOW_TYPE |
                                                                                         PsiFormatUtilBase.TYPE_AFTER |
                                                                                         PsiFormatUtilBase.SHOW_PARAMETERS,
                                                                                         PsiFormatUtilBase.SHOW_TYPE
      )), result);
    }

    return null;
  }

  @NotNull
  private static Pair<HighlightInfo.Builder,HighlightSeverity> createAnnotationForRef(@NotNull GrReferenceElement<?> ref,
                                                      boolean strongError,
                                                      @DetailedDescription @NotNull String message) {
    HighlightDisplayLevel displayLevel = strongError ? HighlightDisplayLevel.ERROR
                                                     : GroovyAccessibilityInspection.getHighlightDisplayLevel(ref.getProject(), ref);
    return Pair.create(GrInspectionUtil.createAnnotationForRef(ref, displayLevel, message), displayLevel.getSeverity());
  }
}

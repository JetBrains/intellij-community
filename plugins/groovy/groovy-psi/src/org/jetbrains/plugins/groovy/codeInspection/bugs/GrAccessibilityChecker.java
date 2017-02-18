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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
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
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Max Medvedev on 21/03/14
 */
public class GrAccessibilityChecker {
  private static final Logger LOG = Logger.getInstance(GrAccessibilityChecker.class);

  private final HighlightDisplayKey myDisplayKey;
  private final boolean myInspectionEnabled;

  public GrAccessibilityChecker(@NotNull GroovyFileBase file, @NotNull Project project) {
    myInspectionEnabled = GroovyAccessibilityInspection.isInspectionEnabled(file, project);
    myDisplayKey = GroovyAccessibilityInspection.findDisplayKey();
  }

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
    return fixes.toArray(new GroovyFix[fixes.size()]);
  }

  @Nullable
  public HighlightInfo checkCodeReferenceElement(GrCodeReferenceElement ref) {
    return checkReferenceImpl(ref);
  }

  private HighlightInfo checkReferenceImpl(GrReferenceElement ref) {
    boolean isCompileStatic = PsiUtil.isCompileStatic(ref);

    if (!needToCheck(ref, isCompileStatic)) return null;

    PsiElement parent = ref.getParent();
    if (parent instanceof GrConstructorCall) {
      String constructorError = checkConstructorCall((GrConstructorCall)parent, ref);
      if (constructorError != null) {
        return createAnnotationForRef(ref, isCompileStatic, constructorError);
      }
    }

    GroovyResolveResult result = ref.advancedResolve();
    String error = checkResolveResult(ref, result) ? GroovyBundle.message("cannot.access", ref.getReferenceName()) : null;
    if (error != null) {
      HighlightInfo info = createAnnotationForRef(ref, isCompileStatic, error);
      registerFixes(ref, result, info);
      return info;
    }

    return null;
  }

  private void registerFixes(GrReferenceElement ref, GroovyResolveResult result, HighlightInfo info) {
    PsiElement element = result.getElement();
    assert element != null;
    ProblemDescriptor descriptor = InspectionManager.getInstance(ref.getProject()).
      createProblemDescriptor(element, element, "", HighlightInfo.convertSeverityToProblemHighlight(info.getSeverity()), true, LocalQuickFix.EMPTY_ARRAY);
    for (GroovyFix fix : buildFixes(ref, result)) {
      QuickFixAction.registerQuickFixAction(info, new LocalQuickFixAsIntentionAdapter(fix, descriptor), myDisplayKey);
    }
  }

  @Nullable
  public HighlightInfo checkReferenceExpression(GrReferenceExpression ref) {
    return checkReferenceImpl(ref);
  }

  private static boolean isStaticallyImportedProperty(GroovyResolveResult result, GrReferenceElement place) {
    final PsiElement parent = place.getParent();
    if (!(parent instanceof GrImportStatement)) return false;

    final PsiElement resolved = result.getElement();
    if (!(resolved instanceof PsiField)) return false;

    final PsiMethod getter = GroovyPropertyUtils.findGetterForField((PsiField)resolved);
    final PsiMethod setter = GroovyPropertyUtils.findSetterForField((PsiField)resolved);

    return getter != null && PsiUtil.isAccessible(place, getter) ||
           setter != null && PsiUtil.isAccessible(place, setter);
  }

  private static boolean checkResolveResult(GrReferenceElement ref, GroovyResolveResult result) {
    return result != null &&
           result.getElement() != null &&
           !result.isAccessible() &&
           !isStaticallyImportedProperty(result, ref);
  }

  private boolean needToCheck(GrReferenceElement ref, boolean isCompileStatic) {
    if (isCompileStatic) return true;
    if (!myInspectionEnabled) return false;
    if (GroovyAccessibilityInspection.isSuppressed(ref)) return false;

    return true;
  }

  private static String checkConstructorCall(GrConstructorCall constructorCall, GrReferenceElement ref) {
    GroovyResolveResult result = constructorCall.advancedResolve();
    if (checkResolveResult(ref, result)) {
      return GroovyBundle.message("cannot.access", PsiFormatUtil.formatMethod((PsiMethod)result.getElement(), PsiSubstitutor.EMPTY,
                                                                              PsiFormatUtilBase.SHOW_NAME |
                                                                              PsiFormatUtilBase.SHOW_TYPE |
                                                                              PsiFormatUtilBase.TYPE_AFTER |
                                                                              PsiFormatUtilBase.SHOW_PARAMETERS,
                                                                              PsiFormatUtilBase.SHOW_TYPE
      ));
    }

    return null;
  }

  @Nullable
  private static HighlightInfo createAnnotationForRef(@NotNull GrReferenceElement ref,
                                                      boolean strongError,
                                                      @NotNull String message) {
    HighlightDisplayLevel displayLevel = strongError ? HighlightDisplayLevel.ERROR
                                                     : GroovyAccessibilityInspection.getHighlightDisplayLevel(ref.getProject(), ref);
    return GrInspectionUtil.createAnnotationForRef(ref, displayLevel, message);
  }
}

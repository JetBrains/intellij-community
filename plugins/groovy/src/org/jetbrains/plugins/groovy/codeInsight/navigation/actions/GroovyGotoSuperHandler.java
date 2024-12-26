// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;

/**
 * @author Medvedev Max
 */
public final class GroovyGotoSuperHandler extends GotoTargetHandler implements LanguageCodeInsightActionHandler {

  private static final Logger LOG = Logger.getInstance(GroovyGotoSuperHandler.class);

  @Override
  protected String getFeatureUsedKey() {
    return GotoSuperAction.FEATURE_ID;
  }

  @Override
  protected GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
    final PsiMember e = findSource(editor, file);
    if (e == null) return null;
    return new GotoData(e, findTargets(e), Collections.emptyList());
  }

  @Override
  protected @NotNull String getChooserTitle(@NotNull PsiElement sourceElement, String name, int length, boolean finished) {
    return CodeInsightBundle.message("goto.super.method.chooser.title");
  }

  @Override
  protected @NotNull String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.super.method.findUsages.title", name);
  }

  @Override
  protected @NotNull String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    final PsiMember source = findSource(editor, file);
    if (source instanceof PsiClass) {
      return GroovyBundle.message("no.super.classes.found");
    }
    else if (source instanceof PsiMethod || source instanceof GrField) {
      return GroovyBundle.message("no.super.method.found");
    }
    else {
      throw new IncorrectOperationException("incorrect element is found: " + (source == null ? "null" : source.getClass().getCanonicalName()));
    }
  }

  private static @Nullable PsiMember findSource(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, GrField.class, PsiClass.class);
  }

  private static PsiElement @NotNull [] findTargets(@NotNull PsiMember e) {
    if (e instanceof PsiClass aClass) {
      List<PsiClass> allSupers = new ArrayList<>(Arrays.asList(aClass.getSupers()));
      for (Iterator<PsiClass> iterator = allSupers.iterator(); iterator.hasNext(); ) {
        PsiClass superClass = iterator.next();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) iterator.remove();
      }
      return allSupers.toArray(PsiClass.EMPTY_ARRAY);
    }
    else if (e instanceof PsiMethod) {
      return getSupers((PsiMethod)e);
    }
    else {
      LOG.assertTrue(e instanceof GrField);
      List<PsiMethod> supers = new ArrayList<>();
      for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)e)) {
        supers.addAll(Arrays.asList(getSupers(method)));
      }
      return supers.toArray(PsiMethod.EMPTY_ARRAY);
    }
  }

  private static PsiMethod @NotNull [] getSupers(PsiMethod method) {
    if (method.isConstructor()) {
      PsiMethod constructorInSuper = JavaPsiConstructorUtil.findConstructorInSuper(method);
      if (constructorInSuper != null) {
        return new PsiMethod[]{constructorInSuper};
      }
    }
    else {
      return method.findSuperMethods(false);
    }

    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public boolean isValidFor(Editor editor, PsiFile file) {
    return file != null && GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType());
  }
}

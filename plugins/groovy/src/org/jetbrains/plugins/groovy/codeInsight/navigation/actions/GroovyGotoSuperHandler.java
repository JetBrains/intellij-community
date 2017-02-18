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
package org.jetbrains.plugins.groovy.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
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
public class GroovyGotoSuperHandler extends GotoTargetHandler implements LanguageCodeInsightActionHandler {

  private static final Logger LOG = Logger.getInstance(GroovyGotoSuperHandler.class);

  @Override
  protected String getFeatureUsedKey() {
    return GotoSuperAction.FEATURE_ID;
  }

  @Override
  protected GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
    final PsiMember e = findSource(editor, file);
    if (e == null) return null;
    return new GotoData(e, findTargets(e), Collections.<AdditionalAction>emptyList());
  }

  @NotNull
  @Override
  protected String getChooserTitle(@NotNull PsiElement sourceElement, String name, int length, boolean finished) {
    return CodeInsightBundle.message("goto.super.method.chooser.title");
  }

  @NotNull
  @Override
  protected String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.super.method.findUsages.title", name);
  }

  @NotNull
  @Override
  protected String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
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

  @Nullable
  private static PsiMember findSource(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, GrField.class, PsiClass.class);
  }

  @NotNull
  private static PsiElement[] findTargets(@NotNull PsiMember e) {
    if (e instanceof PsiClass) {
      PsiClass aClass = (PsiClass)e;
      List<PsiClass> allSupers = new ArrayList<>(Arrays.asList(aClass.getSupers()));
      for (Iterator<PsiClass> iterator = allSupers.iterator(); iterator.hasNext(); ) {
        PsiClass superClass = iterator.next();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) iterator.remove();
      }
      return ContainerUtil.toArray(allSupers, new PsiClass[allSupers.size()]);
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
      return ContainerUtil.toArray(supers, new PsiMethod[supers.size()]);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  private static PsiMethod[] getSupers(PsiMethod method) {
    if (method.isConstructor()) {
      PsiMethod constructorInSuper = PsiSuperMethodUtil.findConstructorInSuper(method);
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

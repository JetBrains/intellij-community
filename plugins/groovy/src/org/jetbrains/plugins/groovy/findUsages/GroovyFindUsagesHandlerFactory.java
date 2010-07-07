/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  public GroovyFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof GrField;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, getFindClassOptions(), getFindMethodOptions(), getFindPackageOptions(), getFindThrowOptions(),
                                     getFindVariableOptions()) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
        final PsiField field = (PsiField)element;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiMethod getter = GroovyPropertyUtils.findGetterForField(field);
          PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);
          if (getter != null || setter != null) {
            final boolean doSearch;
            if ((getter == null || !getter.isPhysical()) && (setter == null || !setter.isPhysical())) {
              doSearch = true;
            }
            else {
              doSearch = Messages.showDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                             FindBundle.message("find.field.accessors.title"),
                                             new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                                             Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE;
            }
            if (doSearch) {
              final List<PsiElement> elements = new ArrayList<PsiElement>();
              if (getter != null) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(getter, ACTION_STRING));
              }
              if (setter != null) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(setter, ACTION_STRING));
              }
              return elements.toArray(new PsiElement[elements.size()]);
            } else {
              return PsiElement.EMPTY_ARRAY;
            }
          }
        }
        return super.getSecondaryElements();
      }
    };
  }
}

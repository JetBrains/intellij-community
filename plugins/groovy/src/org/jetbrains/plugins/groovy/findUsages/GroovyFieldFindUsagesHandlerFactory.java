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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyFieldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  public GroovyFieldFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof GrField;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, this) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        final PsiField field = (PsiField)element;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiMethod[] getters = GroovyPropertyUtils.getAllGettersByField(field);
          PsiMethod[] setters = GroovyPropertyUtils.getAllSettersByField(field);
          if (getters.length + setters.length > 0) {
            final boolean doSearch;
            if (arePhysical(getters) || arePhysical(setters)) {
              if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
              doSearch = Messages.showYesNoDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                             FindBundle.message("find.field.accessors.title"),
                                             Messages.getQuestionIcon()) == Messages.YES;
            }
            else {
              doSearch = true;
            }
            if (doSearch) {
              final List<PsiElement> elements = new ArrayList<>();
              for (PsiMethod getter : getters) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(getter, ACTION_STRING));
              }

              for (PsiMethod setter : setters) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(setter, ACTION_STRING));
              }
              for (Iterator<PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                if (iterator.next() instanceof GrAccessorMethod) iterator.remove();
              }
              return PsiUtilCore.toPsiElementArray(elements);
            }
            else {
              return PsiElement.EMPTY_ARRAY;
            }
          }
        }
        return super.getSecondaryElements();
      }
    };
  }

  private static boolean arePhysical(PsiMethod[] methods) {
    for (PsiMethod method : methods) {
      if (method.isPhysical()) return true;
    }
    return false;
  }
}

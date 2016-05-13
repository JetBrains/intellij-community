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
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Query;
import gnu.trove.THashSet;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxScopeEnlarger;
import org.jetbrains.plugins.javaFX.indexing.JavaFxControllerClassIndex;
import org.jetbrains.plugins.javaFX.indexing.JavaFxIdsIndex;

import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 3/22/13
 */
public class JavaFxImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiMethod) {
      return isImplicitMethodUsage((PsiMethod)element);
    }
    return isImplicitWrite(element);
  }

  public boolean isImplicitMethodUsage(PsiMethod method) {
    if (!isImplicitFxmlAccess(method)) return false;
    final Project project = method.getProject();
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
    final GlobalSearchScope fxmlScope = new JavaFxScopeEnlarger.GlobalFxmlSearchScope(projectScope);
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
    final PsiSearchHelper.SearchCostResult searchCost = RefResolveService.getInstance(project).isUpToDate()
                                                        ? PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES
                                                        : searchHelper.isCheapEnoughToSearch(method.getName(), fxmlScope, null, null);
    if (searchCost == PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
      final Query<PsiReference> query = ReferencesSearch.search(method, fxmlScope);
      return query.findFirst() != null;
    }
    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      if (!isImplicitFxmlAccess(field)) return false;
      final String fieldName = field.getName();
      if (fieldName == null) return false;

      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return false;
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName == null) return false;
      final Project project = element.getProject();
      final Set<VirtualFile> visitedFxmls = new THashSet<>();
      // is the field declared in a controller class?
      final List<VirtualFile> fxmls = JavaFxControllerClassIndex.findFxmlsWithController(project, qualifiedName);
      if (!fxmls.isEmpty()) {
        final Set<String> fxIdFilePaths = JavaFxIdsIndex.getFilePaths(project, fieldName);
        for (VirtualFile fxml : fxmls) {
          visitedFxmls.add(fxml);
          if (fxIdFilePaths.contains(fxml.getPath())) return true;
        }
      }
      // is the field declared in a superclass of a controller class?
      final Set<String> fxIdFilePaths = JavaFxIdsIndex.getFilePaths(project, fieldName);
      if (!fxIdFilePaths.isEmpty()) {
        final Ref<Boolean> refFound = new Ref<>(false);
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final GlobalSearchScope resolveScope = containingClass.getResolveScope();
        JavaFxControllerClassIndex.processControllerClassNames(project, resolveScope, className -> {
          final List<VirtualFile> fxmlCandidates = JavaFxControllerClassIndex.findFxmlsWithController(project, className, resolveScope);
          for (VirtualFile fxml : fxmlCandidates) {
            if (!visitedFxmls.add(fxml)) continue;
            if (fxIdFilePaths.contains(fxml.getPath())) {
              final PsiClass aClass = psiFacade.findClass(className, resolveScope);
              if (InheritanceUtil.isInheritorOrSelf(aClass, containingClass, true)) {
                refFound.set(true);
                return false;
              }
            }
          }
          return true;
        });
        return refFound.get();
      }
    }
    return false;
  }

  private static boolean isImplicitFxmlAccess(PsiModifierListOwner member) {
    return !member.hasModifierProperty(PsiModifier.PUBLIC) &&
           AnnotationUtil.isAnnotated(member, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, false);
  }
}

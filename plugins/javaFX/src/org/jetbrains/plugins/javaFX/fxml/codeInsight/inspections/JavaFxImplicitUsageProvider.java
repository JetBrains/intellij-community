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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxScopeEnlarger;
import org.jetbrains.plugins.javaFX.indexing.JavaFxControllerClassIndex;
import org.jetbrains.plugins.javaFX.indexing.JavaFxIdsIndex;

import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Checks that a non-public field is referenced in fx:id attribute or a non-public method is referenced as an event handler in FXML
 */
public class JavaFxImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiMethod) {
      return isImplicitMethodUsage((PsiMethod)element);
    }
    return isImplicitWrite(element);
  }

  private static boolean isImplicitMethodUsage(@NotNull PsiMethod method) {
    if (!isImplicitFxmlAccess(method)) return false;
    if (isInvokedByFxmlLoader(method)) {
      return true;
    }
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(method.getProject());
    final GlobalSearchScope fxmlScope = new JavaFxScopeEnlarger.GlobalFxmlSearchScope(projectScope);
    return isFxmlUsage(method, fxmlScope);
  }

  private static boolean isFxmlUsage(PsiMember member, GlobalSearchScope scope) {
    final String name = member.getName();
    if (name == null) return false;
    final Project project = member.getProject();
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
    final PsiSearchHelper.SearchCostResult searchCost = RefResolveService.getInstance(project).isUpToDate()
                                                        ? PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES
                                                        : searchHelper.isCheapEnoughToSearch(name, scope, null, null);
    if (searchCost == PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
      final Query<PsiReference> query = ReferencesSearch.search(member, scope);
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
      if (isInjectedByFxmlLoader(field)) {
        return true;
      }
      final Collection<VirtualFile> containingFiles = JavaFxIdsIndex.getContainingFiles(project, fieldName);
      if (containingFiles.isEmpty()) return false;
      // is the field declared in a controller class?
      final List<VirtualFile> fxmls = JavaFxControllerClassIndex.findFxmlsWithController(project, qualifiedName);
      for (VirtualFile fxml : fxmls) {
        if (containingFiles.contains(fxml)) return true;
      }
      // is the field declared in a superclass of a controller class?
      return isFxmlUsage(field, GlobalSearchScope.filesScope(project, containingFiles));
    }
    return false;
  }

  private static boolean isInvokedByFxmlLoader(@NotNull PsiMethod method) {
    return "initialize".equals(method.getName()) &&
           method.getParameterList().getParametersCount() == 0 &&
           isDeclaredInControllerClass(method);
  }

  private static boolean isInjectedByFxmlLoader(@NotNull PsiField field) {
    final String fieldName = field.getName();
    final PsiType fieldType = field.getType();
    return fieldName != null &&
           ("resources".equals(fieldName) && InheritanceUtil.isInheritor(fieldType, "java.util.ResourceBundle") ||
            "location".equals(fieldName) && InheritanceUtil.isInheritor(fieldType, "java.net.URL")) &&
           isDeclaredInControllerClass(field);
  }

  private static boolean isDeclaredInControllerClass(@NotNull PsiMember member) {
    final PsiClass containingClass = member.getContainingClass();
    final String qualifiedName = containingClass != null ? containingClass.getQualifiedName() : null;
    return qualifiedName != null && !JavaFxControllerClassIndex.findFxmlsWithController(member.getProject(), qualifiedName).isEmpty();
  }

  private static boolean isImplicitFxmlAccess(PsiModifierListOwner member) {
    return !member.hasModifierProperty(PsiModifier.PUBLIC) &&
           AnnotationUtil.isAnnotated(member, JavaFxCommonNames.JAVAFX_FXML_ANNOTATION, 0);
  }
}

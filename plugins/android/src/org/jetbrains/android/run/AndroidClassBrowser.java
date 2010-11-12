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

package org.jetbrains.android.run;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 5:53:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidClassBrowser extends BrowseModuleValueActionListener {
  private final ConfigurationModuleSelector myModuleSelector;
  private final String myBaseClassName;
  private final ClassFilter myAdditionalFilter;
  private final String myDialogTitle;
  private final boolean myIncludeLibraryClasses;

  public AndroidClassBrowser(Project project,
                             ConfigurationModuleSelector moduleSelector,
                             String baseClassName,
                             String dialogTitle,
                             boolean includeLibraryClasses,
                             @Nullable ClassFilter additionalFilter) {
    super(project);
    myModuleSelector = moduleSelector;
    myBaseClassName = baseClassName;
    myIncludeLibraryClasses = includeLibraryClasses;
    myAdditionalFilter = additionalFilter;
    myDialogTitle = dialogTitle;
  }

  @Nullable
  protected PsiClass findClass(final String className) {
    return myModuleSelector.findClass(className);
  }

  @Override
  protected String showDialog() {
    Project project = getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass baseClass = facade.findClass(myBaseClassName, ProjectScope.getAllScope(project));
    if (baseClass == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cant.find.class.error", myBaseClassName), CommonBundle.getErrorTitle());
      return null;
    }
    Module module = myModuleSelector.getModule();
    if (module == null) {
      Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), CommonBundle.getErrorTitle());
      return null;
    }
    GlobalSearchScope scope =
      myIncludeLibraryClasses ? module.getModuleWithDependenciesAndLibrariesScope(true) : module.getModuleWithDependenciesScope();
    PsiClass initialSelection = facade.findClass(getText(), scope);
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createInheritanceClassChooser(myDialogTitle, scope, baseClass, initialSelection, new ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          if (aClass.getManager().areElementsEquivalent(aClass, baseClass)) {
            return false;
          }
          if (aClass.isInterface()) return false;
          final PsiModifierList modifierList = aClass.getModifierList();
          if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
          }
          return myAdditionalFilter == null || myAdditionalFilter.isAccepted(aClass);
        }
      });
    chooser.showDialog();
    PsiClass selClass = chooser.getSelected();
    return selClass != null ? selClass.getQualifiedName() : null;
  }
}

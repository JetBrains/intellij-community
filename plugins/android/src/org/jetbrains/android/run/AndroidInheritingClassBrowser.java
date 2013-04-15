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
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 5:53:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidInheritingClassBrowser extends AndroidClassBrowserBase {
  private final String myBaseClassName;

  public AndroidInheritingClassBrowser(@NotNull Project project,
                                       @NotNull ConfigurationModuleSelector moduleSelector,
                                       @NotNull String baseClassName,
                                       @NotNull String dialogTitle,
                                       boolean includeLibraryClasses) {
    super(project, moduleSelector, dialogTitle, includeLibraryClasses);
    myBaseClassName = baseClassName;
  }

  @Override
  protected TreeClassChooser createTreeClassChooser(@NotNull Project project,
                                                    @NotNull GlobalSearchScope scope,
                                                    @Nullable PsiClass initialSelection,
                                                    @NotNull final ClassFilter classFilter) {
    final PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(myBaseClassName, ProjectScope.getAllScope(project));

    if (baseClass == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cant.find.class.error", myBaseClassName), CommonBundle.getErrorTitle());
      return null;
    }
    return TreeClassChooserFactory.getInstance(project).createInheritanceClassChooser(
      myDialogTitle, scope, baseClass, initialSelection, new ClassFilter() {
      @Override
      public boolean isAccepted(PsiClass aClass) {
        if (aClass.getManager().areElementsEquivalent(aClass, baseClass)) {
          return false;
        }
        return classFilter.isAccepted(aClass);
      }
    });
  }
}

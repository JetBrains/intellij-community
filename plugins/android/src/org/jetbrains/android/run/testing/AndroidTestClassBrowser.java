package org.jetbrains.android.run.testing;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.run.AndroidClassBrowserBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestClassBrowser extends AndroidClassBrowserBase {

  public AndroidTestClassBrowser(@NotNull Project project,
                                 @NotNull ConfigurationModuleSelector moduleSelector,
                                 @NotNull String dialogTitle,
                                 boolean includeLibraryClasses) {
    super(project, moduleSelector, dialogTitle, includeLibraryClasses);
  }

  @Nullable
  @Override
  protected TreeClassChooser createTreeClassChooser(@NotNull Project project,
                                                    @NotNull GlobalSearchScope scope,
                                                    @Nullable PsiClass initialSelection, @NotNull final ClassFilter classFilter) {
    return TreeClassChooserFactory.getInstance(project).createNoInnerClassesScopeChooser(myDialogTitle, scope, new ClassFilter() {
      @Override
      public boolean isAccepted(PsiClass aClass) {
        return classFilter.isAccepted(aClass) && JUnitUtil.isTestClass(aClass);
      }
    }, initialSelection);
  }
}

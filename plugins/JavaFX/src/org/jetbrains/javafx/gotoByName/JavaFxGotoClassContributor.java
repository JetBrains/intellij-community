package org.jetbrains.javafx.gotoByName;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxClassNameIndex;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxGotoClassContributor implements GotoClassContributor {
  @Override
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(JavaFxClassNameIndex.KEY, project);
    return ArrayUtil.toStringArray(classNames);
  }

  @Override
  public NavigationItem[] getItemsByName(final String name,
                                         final String pattern,
                                         final Project project,
                                         final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? ProjectScope.getAllScope(project)
                                    : GlobalSearchScope.projectScope(project);
    final Collection<JavaFxClassDefinition> classes = StubIndex.getInstance().get(JavaFxClassNameIndex.KEY, name, project, scope);
    return classes.toArray(new NavigationItem[classes.size()]);
  }

  @Override
  public String getQualifiedName(final NavigationItem item) {
    if (item instanceof JavaFxClassDefinition) {
      final JavaFxQualifiedName qualifiedName = ((JavaFxClassDefinition)item).getQualifiedName();
      if (qualifiedName != null) {
        return qualifiedName.toString();
      }
    }
    return null;
  }
}

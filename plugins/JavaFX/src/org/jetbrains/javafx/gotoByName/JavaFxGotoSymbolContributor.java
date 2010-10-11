package org.jetbrains.javafx.gotoByName;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxClassNameIndex;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxFunctionNameIndex;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxVariableNameIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxGotoSymbolContributor implements ChooseByNameContributor {
  @Override
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Set<String> symbols = new HashSet<String>();
    symbols.addAll(StubIndex.getInstance().getAllKeys(JavaFxClassNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(JavaFxFunctionNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(JavaFxVariableNameIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  @Override
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? ProjectScope.getAllScope(project)
                                    : GlobalSearchScope.projectScope(project);

    final List<NavigationItem> symbols = new ArrayList<NavigationItem>();
    symbols.addAll(StubIndex.getInstance().get(JavaFxClassNameIndex.KEY, name, project, scope));
    symbols.addAll(StubIndex.getInstance().get(JavaFxFunctionNameIndex.KEY, name, project, scope));
    symbols.addAll(StubIndex.getInstance().get(JavaFxVariableNameIndex.KEY, name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }
}

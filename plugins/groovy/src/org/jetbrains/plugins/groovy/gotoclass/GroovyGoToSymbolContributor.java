package org.jetbrains.plugins.groovy.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrShortClassNameIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyGoToSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<String>();
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrShortClassNameIndex.KEY));
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY));
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY));
    return symbols.toArray(new String[symbols.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);
    
    List<NavigationItem> symbols = new ArrayList<NavigationItem>();
    symbols.addAll(StubIndex.getInstance().get(GrShortClassNameIndex.KEY, name, project, scope));
    symbols.addAll(StubIndex.getInstance().get(GrFieldNameIndex.KEY, name, project, scope));
    symbols.addAll(StubIndex.getInstance().get(GrMethodNameIndex.KEY, name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }

}
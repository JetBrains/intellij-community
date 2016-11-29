/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyGoToSymbolContributor implements ChooseByNameContributor {
  @Override
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<>();
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);

    List<NavigationItem> symbols = new ArrayList<>();
    symbols.addAll(StubIndex.getElements(GrFieldNameIndex.KEY, name, project, scope, GrField.class));
    symbols.addAll(StubIndex.getElements(GrMethodNameIndex.KEY, name, project, scope, GrMethod.class));
    symbols.addAll(StubIndex.getElements(GrAnnotationMethodNameIndex.KEY, name, project, scope, GrAnnotationMethod.class));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }

}

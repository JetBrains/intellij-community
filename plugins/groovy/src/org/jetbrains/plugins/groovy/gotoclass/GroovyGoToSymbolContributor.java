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

import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

/**
 * @author ilyas
 */
public class GroovyGoToSymbolContributor implements ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    StubIndex index = StubIndex.getInstance();
    if (!index.processAllKeys(GrFieldNameIndex.KEY, processor, scope, filter)) return;
    if (!index.processAllKeys(GrMethodNameIndex.KEY, processor, scope, filter)) return;
    if (!index.processAllKeys(GrAnnotationMethodNameIndex.KEY, processor, scope, filter)) return;
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    StubIndex index = StubIndex.getInstance();
    Project project = parameters.getProject();
    GlobalSearchScope scope = parameters.getSearchScope();
    IdFilter filter = parameters.getIdFilter();
    if (!index.processElements(GrFieldNameIndex.KEY, name, project, scope, filter, GrField.class, processor)) return;
    if (!index.processElements(GrMethodNameIndex.KEY, name, project, scope, filter, GrMethod.class, processor)) return;
    if (!index.processElements(GrAnnotationMethodNameIndex.KEY, name, project, scope, filter, GrAnnotationMethod.class, processor)) return;
  }
}

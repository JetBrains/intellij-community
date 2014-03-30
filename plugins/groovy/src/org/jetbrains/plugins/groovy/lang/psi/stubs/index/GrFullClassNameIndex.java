/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GrFullClassNameIndex extends IntStubIndexExtension<PsiClass> {
  public static final StubIndexKey<Integer,PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn");

  private static final GrFullClassNameIndex ourInstance = new GrFullClassNameIndex();
  public static GrFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }

  public Collection<PsiClass> get(final Integer integer, final Project project, final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), integer, project, new GrSourceFilterScope(scope), PsiClass.class);
  }
}
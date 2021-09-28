// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

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

  @Override
  @NotNull
  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }

  @Override
  public Collection<PsiClass> get(@NotNull final Integer integer, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), integer, project, new GrSourceFilterScope(scope), PsiClass.class);
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}

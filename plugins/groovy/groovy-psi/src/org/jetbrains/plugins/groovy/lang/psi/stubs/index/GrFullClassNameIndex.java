// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.CharSequenceHashStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.util.Collection;

/**
 * @deprecated consider to use {@link GrFullClassNameStringIndex}
 */
@Deprecated(forRemoval = true)
public class GrFullClassNameIndex extends CharSequenceHashStubIndexExtension<PsiClass> {
  public static final StubIndexKey<CharSequence, PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn");

  @Override
  public @NotNull StubIndexKey<CharSequence, PsiClass> getKey() {
    return KEY;
  }

  /**
   * @deprecated Deprecated base method, please use {@link StubIndex#getElements(StubIndexKey, Object, Project, GlobalSearchScope, Class)}
   */
  @Deprecated
  @Override
  public Collection<PsiClass> get(@NotNull CharSequence name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PsiClass.class);
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }

  @Override
  public boolean doesKeyMatchPsi(@NotNull CharSequence key, @NotNull PsiClass aClass) {
    return key.equals(aClass.getQualifiedName());
  }
}

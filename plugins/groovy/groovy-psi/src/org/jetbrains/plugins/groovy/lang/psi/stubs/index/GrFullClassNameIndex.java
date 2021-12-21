// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.AbstractStubIndex;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.CharSequenceHashInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GrFullClassNameIndex extends AbstractStubIndex<CharSequence, PsiClass> {
  public static final StubIndexKey<CharSequence, PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn");

  private static final GrFullClassNameIndex ourInstance = new GrFullClassNameIndex();

  public static GrFullClassNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  @NotNull
  public StubIndexKey<CharSequence, PsiClass> getKey() {
    return KEY;
  }

  @Override
  public @NotNull KeyDescriptor<CharSequence> getKeyDescriptor() {
    return new CharSequenceHashInlineKeyDescriptor();
  }

  @Override
  public Collection<PsiClass> get(@NotNull CharSequence name, @NotNull Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), name, project, new GrSourceFilterScope(scope), PsiClass.class);
  }

  @Override
  public int getVersion() {
    return 1 + GrStubUtils.GR_STUB_VERSION;
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

/**
 * @author ilyas
 */
public class GrDirectInheritorsIndex extends StringStubIndexExtension<GrReferenceList> {
  public static final StubIndexKey<String, GrReferenceList> KEY = StubIndexKey.createIndexKey("gr.class.super");

  @Override
  @NotNull
  public StubIndexKey<String, GrReferenceList> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }

}

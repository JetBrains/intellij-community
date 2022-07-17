// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

/**
 * @author ilyas
 */
public class GrFieldNameIndex extends StringStubIndexExtension<GrField> {
  public static final StubIndexKey<String, GrField> KEY = StubIndexKey.createIndexKey("gr.field.name");

  @Override
  @NotNull
  public StubIndexKey<String, GrField> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}

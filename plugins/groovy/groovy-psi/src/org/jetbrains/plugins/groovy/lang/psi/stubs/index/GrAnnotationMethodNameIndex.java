// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

/**
 * @author ilyas
 */
public class GrAnnotationMethodNameIndex extends StringStubIndexExtension<GrAnnotationMethod> {
  public static final StubIndexKey<String, GrAnnotationMethod> KEY = StubIndexKey.createIndexKey("gr.annot.method.name");

  @Override
  @NotNull
  public StubIndexKey<String, GrAnnotationMethod> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}

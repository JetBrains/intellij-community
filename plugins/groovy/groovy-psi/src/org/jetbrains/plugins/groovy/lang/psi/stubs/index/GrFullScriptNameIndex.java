// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.CharSequenceHashStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

/**
 * @author ilyas
 */
public class GrFullScriptNameIndex extends CharSequenceHashStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<CharSequence, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.fqn");

  private static final GrFullScriptNameIndex ourInstance = new GrFullScriptNameIndex();

  public static GrFullScriptNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION + 1;
  }

  @Override
  @NotNull
  public StubIndexKey<CharSequence, GroovyFile> getKey() {
    return KEY;
  }

}

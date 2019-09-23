// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author sergey.evdokimov
 */
public class GantScriptTypeDetector extends GroovyScriptTypeDetector {

  public GantScriptTypeDetector() {
    super(GantScriptType.INSTANCE);
  }

  @Override
  public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
    String name = script.getName();
    return name.endsWith(GantScriptType.DEFAULT_EXTENSION);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.GroovyBundle;

public class WithStatementsSurrounder extends GroovySimpleManyStatementsSurrounder {

  @Override
  protected String getReplacementTokens() {
    return "with(a){\n}";
  }

  @Override
  public String getTemplateDescription() {
    return GroovyBundle.message("surround.with.with");
  }
}

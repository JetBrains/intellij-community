// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * Provides the shouldFail() { ... }  surround with. It follows a Template Method pattern.
 * @author Hamlet D'Arcy
 */
public class ShouldFailWithTypeStatementsSurrounder extends GroovySimpleManyStatementsSurrounder {

  @Override
  protected String getReplacementTokens() {
    return "shouldFail(a){\n}";
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.shouldFail");
  }
}

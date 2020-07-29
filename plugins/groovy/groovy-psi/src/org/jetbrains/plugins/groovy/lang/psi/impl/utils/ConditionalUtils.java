// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public final class ConditionalUtils {

  private ConditionalUtils() {}

  public static GrStatement stripBraces(GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      final GrBlockStatement block = (GrBlockStatement) branch;
      final GrOpenBlock codeBlock = block.getBlock();
      final GrStatement[] statements = codeBlock.getStatements();
      if (statements.length == 1) {
        return statements[0];
      } else {
        return block;
      }
    } else {
      return branch;
    }
  }
}

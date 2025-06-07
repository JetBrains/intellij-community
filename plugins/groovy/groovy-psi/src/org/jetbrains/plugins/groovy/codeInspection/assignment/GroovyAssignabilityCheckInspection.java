// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.codeInspection.assignment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitor;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyAssignabilityCheckInspection extends BaseInspection {

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new GroovyTypeCheckVisitor();
  }
}

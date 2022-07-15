// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool;

/**
 * @author Vladislav.Soroka
 */
public abstract class GradleBaseInspection extends GroovyLocalInspectionTool {
  @Override
  public String @NotNull [] getGroupPath() {
    return new String[]{GradleConstants.GRADLE_NAME, getGroupDisplayName()}; //NON-NLS GRADLE_NAME
  }
}

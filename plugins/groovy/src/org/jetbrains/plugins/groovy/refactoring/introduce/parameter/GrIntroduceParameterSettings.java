// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceSettings;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

/**
 * @author Maxim.Medvedev
 */
public interface GrIntroduceParameterSettings extends GrIntroduceSettings, IntroduceParameterInfo {
  boolean generateDelegate();

  IntList parametersToRemove();

  /**
   * @see IntroduceParameterRefactoring
   */
  @MagicConstant(valuesFromClass = IntroduceParameterRefactoring.class)
  int replaceFieldsWithGetters();

  boolean declareFinal();

  boolean removeLocalVariable();

  @Override
  @Nullable
  GrVariable getVar();

  @Nullable
  GrExpression getExpression();

  @Override
  @Nullable
  StringPartInfo getStringPartInfo();
}

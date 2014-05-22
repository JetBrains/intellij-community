/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import gnu.trove.TIntArrayList;
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

  TIntArrayList parametersToRemove();

  /**
   * @see com.intellij.refactoring.IntroduceParameterRefactoring
   */
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

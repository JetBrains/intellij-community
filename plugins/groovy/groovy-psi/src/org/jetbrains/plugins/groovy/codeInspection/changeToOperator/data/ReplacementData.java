/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public final class ReplacementData {

  private final String myReplacement;
  private final NotNullFunction<GrMethodCallExpression, GrExpression> myElementToReplace;

  public ReplacementData(@NotNull String replacement, @Nullable NotNullFunction<GrMethodCallExpression, GrExpression> elementToReplace) {
    this.myReplacement = replacement;
    this.myElementToReplace = elementToReplace;
  }

  @NotNull
  @Contract(pure = true)
  public String getReplacement() {
    return myReplacement;
  }

  @NotNull
  public GrExpression getElementToReplace(@NotNull GrMethodCallExpression call) {
    return myElementToReplace == null ? call : myElementToReplace.fun(call);
  }
}

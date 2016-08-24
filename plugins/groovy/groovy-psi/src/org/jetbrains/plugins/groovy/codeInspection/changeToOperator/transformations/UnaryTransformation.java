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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.MethodCallData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.OptionsData;

import static com.google.common.base.MoreObjects.firstNonNull;

/**
 * e.g.
 * !a.asBoolean()    → !a
 * a.asBoolean()     → !!a
 * if(a.asBoolean()) → if(a)
 */
public class UnaryTransformation extends Transformation {
  public UnaryTransformation(@Nullable IElementType operator) {
    super(operator);
  }

  @Override
  @Nullable
  public String getReplacement(MethodCallData call, OptionsData options) {
    String prefix = getPrefix(call, options);
    String base = call.getBase();
    if ((prefix == null) || (base == null)) return null;

    return prefix + base;
  }

  @Nullable
  protected String getPrefix(MethodCallData call, OptionsData optionsData) {
    return firstNonNull(operator, "").toString();
  }
}
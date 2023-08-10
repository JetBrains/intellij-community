/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.control;

import com.intellij.codeInspection.options.OptPane;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public class GroovyIfStatementWithTooManyBranchesInspection extends GroovyIfStatementWithTooManyBranchesInspectionBase {

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message("if.statement.with.too.many.branches.max.option"), 2, 100));
  }
}

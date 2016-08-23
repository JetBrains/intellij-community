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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

public class GrRemoveModifierFix extends GrModifierFix {

  public GrRemoveModifierFix(@NotNull @GrModifier.GrModifierConstant String modifier) {
    this(modifier, GroovyInspectionBundle.message("unnecessary.modifier.remove", modifier));
  }

  public GrRemoveModifierFix(@NotNull @GrModifier.GrModifierConstant String modifier, @NotNull String text) {
    super(text, modifier, false, GrModifierFix.MODIFIER_LIST_CHILD);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled((Runnable)() -> super.doFix(project, descriptor));
  }
}

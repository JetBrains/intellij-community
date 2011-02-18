/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.refactoring.HelpID;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

/**
 * @author ilyas
 */
public class GroovyIntroduceVariableHandler extends GroovyIntroduceVariableBase {
  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  protected GroovyIntroduceVariableDialog getDialog(GrIntroduceContext context) {
    final GroovyVariableValidator validator = new GroovyVariableValidator(context);
    String[] possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(context.expression, validator);
    return new GroovyIntroduceVariableDialog(context, validator, possibleNames);
  }
}

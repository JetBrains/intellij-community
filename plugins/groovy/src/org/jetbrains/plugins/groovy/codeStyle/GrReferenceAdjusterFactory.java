/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.ReferenceAdjuster;
import com.intellij.psi.codeStyle.ReferenceAdjusterFactory;

/**
 * @author Max Medvedev
 */
public class GrReferenceAdjusterFactory implements ReferenceAdjusterFactory {
  @Override
  public ReferenceAdjuster createReferenceAdjuster(boolean useFqInJavadoc, boolean useFqInCode) {
    return new GrReferenceAdjuster(useFqInJavadoc, useFqInCode);
  }

  @Override
  public ReferenceAdjuster createReferenceAdjuster(Project project) {
    return new GrReferenceAdjuster(CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class));
  }
}

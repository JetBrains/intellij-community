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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GroovyScriptRunConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement mySourceElement;

  public GroovyScriptRunConfigurationProducer() {
    super(GroovyScriptRunConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final PsiElement element = location.getPsiElement();
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return null;
    }

    GroovyFile groovyFile = (GroovyFile)file;
    if (groovyFile.isScript()) {
      mySourceElement = element;
      final RunnerAndConfigurationSettings settings = GroovyScriptRunConfigurationType.getInstance().createConfigurationByLocation(location);
      if (settings != null) {
        final GroovyScriptRunConfiguration configuration = (GroovyScriptRunConfiguration)settings.getConfiguration();
        GroovyScriptType.getScriptType(groovyFile).tuneConfiguration(groovyFile, configuration, location);
        return (RunnerAndConfigurationSettingsImpl)settings;
      }
    }

    return null;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}

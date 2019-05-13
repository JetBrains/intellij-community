/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.application.options.CodeStyleImportsPanelBase;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.*;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrCodeStyleImportsPanel extends CodeStyleImportsPanelBase {
  private JCheckBox myCbUseFQClassNamesInJavaDoc;
  
  @Override
  protected void fillCustomOptions(OptionGroup group) {
    myCbUseFQClassNamesInJavaDoc = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names.in.javadoc"));
    group.add(myCbUseFQClassNamesInJavaDoc);
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    GroovyCodeStyleSettings groovySettings = getGroovySettings(settings);
    applyLayoutSettings(groovySettings);
    groovySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC = myCbUseFQClassNamesInJavaDoc.isSelected();
  }

  private static GroovyCodeStyleSettings getGroovySettings(CodeStyleSettings settings) {
    return settings.getCustomSettings(GroovyCodeStyleSettings.class);
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    GroovyCodeStyleSettings groovySettings = getGroovySettings(settings);
    resetLayoutSettings(groovySettings);
    myCbUseFQClassNamesInJavaDoc.setSelected(groovySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    GroovyCodeStyleSettings grSettings = getGroovySettings(settings);
    return isModified(myCbUseFQClassNamesInJavaDoc, grSettings.USE_FQ_CLASS_NAMES_IN_JAVADOC) 
           || isModifiedLayoutSettings(grSettings);
  }
  
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;

public class GroovyCodeStyleSettingsFacadeImpl extends GroovyCodeStyleSettingsFacade {
  private final Project myProject;

  public GroovyCodeStyleSettingsFacadeImpl(Project project) {
    myProject = project;
  }

  private GroovyCodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getCustomSettings(GroovyCodeStyleSettings.class);
  }

  @Override
  public boolean useFqClassNames() {
    return getSettings().USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean useFqClassNamesInJavadoc() {
    return getSettings().USE_FQ_CLASS_NAMES_IN_JAVADOC;
  }

  @Override
  public int staticFieldsOrderWeight() {
    return getSettings().STATIC_FIELDS_ORDER_WEIGHT;
  }

  @Override
  public int fieldsOrderWeight() {
    return getSettings().FIELDS_ORDER_WEIGHT;
  }

  @Override
  public int staticMethodsOrderWeight() {
    return getSettings().STATIC_METHODS_ORDER_WEIGHT;
  }

  @Override
  public int methodsOrderWeight() {
    return getSettings().METHODS_ORDER_WEIGHT;
  }

  @Override
  public int staticInnerClassesOrderWeight() {
    return getSettings().STATIC_INNER_CLASSES_ORDER_WEIGHT;
  }

  @Override
  public int innerClassesOrderWeight() {
    return getSettings().INNER_CLASSES_ORDER_WEIGHT;
  }

  @Override
  public int constructorsOrderWeight() {
    return getSettings().CONSTRUCTORS_ORDER_WEIGHT;
  }

  @Override
  public boolean insertInnerClassImports() {
    return getSettings().INSERT_INNER_CLASS_IMPORTS;
  }
}

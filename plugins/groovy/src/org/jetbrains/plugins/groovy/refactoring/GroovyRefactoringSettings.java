// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
@State(name = "GroovyRefactoringSettings", storages = @Storage("other.xml"))
public class GroovyRefactoringSettings implements PersistentStateComponent<GroovyRefactoringSettings> {
  public static GroovyRefactoringSettings getInstance() {
    return ApplicationManager.getApplication().getService(GroovyRefactoringSettings.class);
  }

  @Override
  public GroovyRefactoringSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GroovyRefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }


}

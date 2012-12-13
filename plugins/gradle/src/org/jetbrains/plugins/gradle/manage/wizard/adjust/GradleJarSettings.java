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
package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleJar;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 12/12/12 2:16 PM
 */
public class GradleJarSettings implements GradleProjectStructureNodeSettings {

  @NotNull private final JComponent myComponent;

  public GradleJarSettings(@NotNull GradleJar jar) {
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    builder.add(new JLabel(GradleBundle.message("gradle.import.structure.settings.label.jar.path")));
    NamePathComponent component = new NamePathComponent("", "  ", "", "", false);
    component.setNameComponentVisible(false);
    component.setPath(jar.getPath());
    component.getPathPanel().setEditable(false);
    builder.add(component, GradleProjectSettingsBuilder.InsetSize.SMALL);
    
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return true;
  }

  @Override
  public void refresh() {
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}

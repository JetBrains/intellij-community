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
package org.jetbrains.plugins.groovy.mvc.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class ModuleCellRenderer extends ListCellRendererWrapper<Module> {
  public ModuleCellRenderer(ListCellRenderer renderer) {
    super();
  }

  @Override
  public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
    if (module != null) {
      setIcon(ModuleType.get(module).getIcon());
      setText(module.getName());
    }
  }
}

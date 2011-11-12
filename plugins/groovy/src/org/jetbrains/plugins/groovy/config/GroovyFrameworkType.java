/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;

/**
 * @author nik
 */
public class GroovyFrameworkType extends FrameworkTypeEx {
  public GroovyFrameworkType() {
    super("Groovy");
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleProvider createProvider() {
    return new GroovyFrameworkSupportProvider();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Groovy";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }
}

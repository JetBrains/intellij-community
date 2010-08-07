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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GroovyScriptType {
  public static final ExtensionPointName<GroovyScriptTypeEP> EP_NAME = ExtensionPointName.create("org.intellij.groovy.scriptType");
  public static final GroovyScriptType DEFAULT_TYPE = new GroovyScriptType() {
    @Override
    public boolean isSpecificScriptFile(GroovyFile file) {
      return true;
    }

    @Override
    public boolean shouldBeCompiled(GroovyFile script) {
      return true;
    }

    @NotNull
    @Override
    public Icon getScriptIcon() {
      return GroovyIcons.GROOVY_ICON_16x16;
    }

    @Override
    public GroovyScriptRunner getRunner() {
      return new DefaultGroovyScriptRunner();
    }
  };

  @NotNull
  public static GroovyScriptType getScriptType(@NotNull GroovyFile script) {
    assert script.isScript();
    for (final GroovyScriptTypeEP typeEP : EP_NAME.getExtensions()) {
      final GroovyScriptType descriptor = typeEP.getTypeDescriptor();
      if (descriptor.isSpecificScriptFile(script)) {
        return descriptor;
      }
    }
    return DEFAULT_TYPE;
  }


  public abstract boolean isSpecificScriptFile(GroovyFile file);

  @NotNull
  public abstract Icon getScriptIcon();

  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull GroovyScriptRunConfiguration configuration, Location location) {
  }

  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return baseScope;
  }

  @Nullable
  public GroovyScriptRunner getRunner() {
    return null;
  }

  public List<String> appendImplicitImports(@NotNull GroovyFile file) {
    return Collections.emptyList();
  }

  public boolean shouldBeCompiled(GroovyFile script) {
    return false;
  }

  public boolean isConfigurationByLocation(@NotNull GroovyScriptRunConfiguration existing, @NotNull Location location) {
    return true;
  }
}

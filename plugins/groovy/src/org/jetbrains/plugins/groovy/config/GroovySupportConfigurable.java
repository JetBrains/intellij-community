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
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.library.FrameworkSupportWithLibrary;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
* @author peter
*/
public class GroovySupportConfigurable extends FrameworkSupportConfigurable implements FrameworkSupportWithLibrary {
  @NotNull
  @Override
  public CustomLibraryDescription createLibraryDescription() {
    return new GroovyLibraryDescription();
  }

  @Override
  public List<? extends FrameworkVersion> getVersions() {
    return Collections.singletonList(getSelectedVersion());
  }

  public JComponent getComponent() {
    return null;
  }

  @Override
  public boolean isLibraryOnly() {
    return true;
  }

  public void addSupport(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel, @Nullable Library library) {
  }
}

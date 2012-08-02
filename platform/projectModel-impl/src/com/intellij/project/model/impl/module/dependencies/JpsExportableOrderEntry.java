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
package com.intellij.project.model.impl.module.dependencies;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.project.model.impl.module.JpsRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * @author nik
 */
public abstract class JpsExportableOrderEntry<E extends JpsDependencyElement> extends JpsOrderEntry<E> implements ExportableOrderEntry {
  public JpsExportableOrderEntry(JpsRootModel rootModel, E dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public boolean isExported() {
    final JpsJavaDependencyExtension extension = getExtension();
    return extension != null && extension.isExported();
  }

  @Override
  public void setExported(boolean value) {
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(myDependencyElement).setExported(value);
  }

  @NotNull
  @Override
  public DependencyScope getScope() {
    final JpsJavaDependencyExtension extension = getExtension();
    return extension != null ? DependencyScope.valueOf(extension.getScope().name()) : DependencyScope.COMPILE;
  }

  @Nullable
  private JpsJavaDependencyExtension getExtension() {
    return JpsJavaExtensionService.getInstance().getDependencyExtension(myDependencyElement);
  }

  @Override
  public void setScope(@NotNull DependencyScope scope) {
    final JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(myDependencyElement);
    extension.setScope(JpsJavaDependencyScope.valueOf(scope.name()));
  }
}

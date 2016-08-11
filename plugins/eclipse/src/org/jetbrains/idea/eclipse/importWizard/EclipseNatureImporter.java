/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class EclipseNatureImporter {
  public static final ExtensionPointName<EclipseNatureImporter> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.eclipse.natureImporter");

  @NotNull
  public abstract String getNatureName();
  public abstract Set<String> getProvidedCons();

  public abstract void doImport(@NotNull Project project, @NotNull List<Module> modules);

  public static Set<String> getAllDefinedCons() {
    final Set<String> allCons = new HashSet<>();
    allCons.add(EclipseXml.GROOVY_SUPPORT);
    allCons.add(EclipseXml.GROOVY_DSL_CONTAINER);

    for (EclipseNatureImporter provider : Extensions.getExtensions(EP_NAME)) {
      allCons.addAll(provider.getProvidedCons());
    }
    return allCons;
  }
  
  public static List<String> getDefaultNatures() {
    return Arrays.asList(EclipseXml.JAVA_NATURE, EclipseXml.JREBEL_NATURE, EclipseXml.SONAR_NATURE);
  }
}

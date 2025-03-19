// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class EclipseNatureImporter {
  public static final ExtensionPointName<EclipseNatureImporter> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.eclipse.natureImporter");

  public abstract @NotNull String getNatureName();
  public abstract Set<String> getProvidedCons();

  public abstract void doImport(@NotNull Project project, @NotNull List<Module> modules);

  public static Set<String> getAllDefinedCons() {
    final Set<String> allCons = new HashSet<>();
    allCons.add(EclipseXml.GROOVY_SUPPORT);
    allCons.add(EclipseXml.GROOVY_DSL_CONTAINER);

    for (EclipseNatureImporter provider : EP_NAME.getExtensionList()) {
      allCons.addAll(provider.getProvidedCons());
    }
    return allCons;
  }

  public static List<String> getDefaultNatures() {
    return Arrays.asList(EclipseXml.JAVA_NATURE, EclipseXml.JREBEL_NATURE, EclipseXml.SONAR_NATURE);
  }
}

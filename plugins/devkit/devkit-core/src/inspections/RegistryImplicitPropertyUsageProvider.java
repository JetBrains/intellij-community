// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class RegistryImplicitPropertyUsageProvider implements ImplicitPropertyUsageProvider {
  @Override
  public boolean isUsed(@NotNull Property property) {
    if (RegistryPropertiesAnnotatorKt.isRegistryPropertiesFile(property.getContainingFile())) {
      final String name = property.getName();
      return name != null && RegistryPropertiesAnnotatorKt.isImplicitUsageKey(name);
    }
    return false;
  }
}

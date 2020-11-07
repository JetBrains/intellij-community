// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    if (RegistryPropertiesAnnotator.isRegistryPropertiesFile(property.getContainingFile())) {
      final String name = property.getName();
      return name != null && RegistryPropertiesAnnotator.isImplicitUsageKey(name);
    }
    return false;
  }
}

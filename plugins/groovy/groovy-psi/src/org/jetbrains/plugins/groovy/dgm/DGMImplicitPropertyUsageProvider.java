// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

final class DGMImplicitPropertyUsageProvider implements ImplicitPropertyUsageProvider {
  @Override
  public boolean isUsed(@NotNull Property property) {
    if (DGMUtil.isInDGMFile(property)) {
      String name = property.getName();
      return ArrayUtil.find(DGMUtil.KEYS, name) >= 0;
    }
    return  false;
  }
}

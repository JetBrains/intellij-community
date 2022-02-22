// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testutils;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.codeInspection.resources.TestClassPropertyProvider;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;

import java.util.Collection;

public class JunitTestClassPropertyProvider implements TestClassPropertyProvider {
  private static final String PER_CLASS_PROPERTY_KEY = "junit.jupiter.testinstance.lifecycle.default";
  @Override
  public boolean hasTestClassProperty(@NotNull Module classModule) {
    final GlobalSearchScope globalSearchScope = GlobalSearchScope.moduleRuntimeScope(classModule, true);
    Collection<Property> property =
      PropertyKeyIndex.getInstance().get(PER_CLASS_PROPERTY_KEY, classModule.getProject(), globalSearchScope);
    if (property == null) return false;
    Property item = ContainerUtil.getFirstItem(property);
    if (item == null) return false;
    String value = item.getValue();
    return "PER_CLASS".equalsIgnoreCase(value);
  }
}

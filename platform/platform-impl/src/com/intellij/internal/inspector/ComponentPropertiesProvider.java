// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

public interface ComponentPropertiesProvider<T> {

  ExtensionPointName<ComponentPropertiesProvider<?>> PROPERTIES_EP_PROVIDER_NAME = ExtensionPointName.create("com.intellij.uiiPropertiesProvider");

  List<PropertyBean> getProperties(T component);
  Class<T> getComponentClass();
}

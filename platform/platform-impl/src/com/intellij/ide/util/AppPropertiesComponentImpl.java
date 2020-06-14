// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;

@State(name = "PropertiesComponent", storages = {
  @Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED),
  @Storage(value = "options.xml", roamingType = RoamingType.DISABLED, deprecated = true)
}, reportStatistic = false)
public class AppPropertiesComponentImpl extends PropertiesComponentImpl {
}

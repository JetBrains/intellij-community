// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "PropertiesComponent", storages = {
  @Storage(value = Storage.NOT_ROAMABLE_FILE, roamingType = RoamingType.DISABLED),
  @Storage(value = "options.xml", roamingType = RoamingType.DISABLED, deprecated = true)
})
public class AppPropertiesComponentImpl extends PropertiesComponentImpl {
}

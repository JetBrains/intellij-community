// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import java.util.Map;

public interface VersionCatalogsModel {
  Map<String, String> getCatalogsLocations();
}

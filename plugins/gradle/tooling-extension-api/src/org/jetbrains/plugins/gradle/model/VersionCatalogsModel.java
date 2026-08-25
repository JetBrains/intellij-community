// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import java.io.File;
import java.util.Map;

@SuppressWarnings("IO_FILE_USAGE")
public interface VersionCatalogsModel {
  Map<String, File> getCatalogsLocations();
}

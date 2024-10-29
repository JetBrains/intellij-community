// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.javaModel;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;

public interface JavaGradleManifestModel extends Serializable {

  @NotNull Map<String, String> getManifestAttributes();
}

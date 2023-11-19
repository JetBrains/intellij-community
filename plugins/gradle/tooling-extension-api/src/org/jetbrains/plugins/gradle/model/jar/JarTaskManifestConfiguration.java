// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.jar;

import java.io.Serializable;
import java.util.Map;

public interface JarTaskManifestConfiguration extends Serializable {
  Map<String, String> getProjectIdentityPathToModuleName();
}

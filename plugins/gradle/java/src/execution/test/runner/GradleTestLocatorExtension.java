// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface GradleTestLocatorExtension extends SMTestLocator {
  @NotNull ExtensionPointName<GradleTestLocatorExtension> EP_NAME =
    ExtensionPointName.create("org.jetbrains.plugins.gradle.testLocator");
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util;

import org.jetbrains.annotations.Nullable;

interface ApplicationPluginAccessor {

  @Nullable String getMainClass();
}

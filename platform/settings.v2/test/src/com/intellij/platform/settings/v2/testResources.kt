// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.testFramework.PlatformTestUtil

/** Returns the absolute path of [relativePath] under the `resources` root of this test module, from the community home. */
internal fun testResourcePath(relativePath: String): String =
  "${PlatformTestUtil.getCommunityPath()}/platform/settings.v2/test/resources/$relativePath"

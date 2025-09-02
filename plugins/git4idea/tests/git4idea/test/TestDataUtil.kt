// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.TestDataPath
import java.nio.file.Path

@TestDataPath("\$CONTENT_ROOT/testData")
object TestDataUtil {
  @JvmStatic
  val basePath: Path by lazy {
    val pluginRoot = Path.of(PluginPathManager.getPluginHomePath("git4idea"))
    pluginRoot.resolve("testData")
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.description.Description
import org.junit.Test
import java.io.File

class ConfigImportHelperTest : BareTestFixtureTestCase() {
  @Test fun configDirectoryIsValidForImport() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    runInEdtAndWait { StoreUtil.saveSettings(ApplicationManager.getApplication(), true) }
    val config = File(PathManager.getConfigPath())
    assertThat(ConfigImportHelper.isConfigDirectory(config))
      .`as`(description { "${config} exists:${config.exists()} options=${File(config, "options").list().asList()}" })
      .isTrue()
  }

  private fun description(block: () -> String) = object : Description() { override fun value() = block() }
}
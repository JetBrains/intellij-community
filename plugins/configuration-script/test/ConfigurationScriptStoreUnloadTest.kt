package com.intellij.configurationScript

import com.intellij.configurationScript.providers.MyProjectStore
import com.intellij.configurationScript.providers.ReadOnlyStorage
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.runInLoadComponentStateMode
import org.junit.ClassRule
import org.junit.Test

class ConfigurationScriptStoreUnloadTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun unloadedComponentLeavesNoStorageInManager() = runInEdtAndWait {
    val project = projectRule.project
    assertThat(project.stateStore).isInstanceOf(MyProjectStore::class.java)

    val pluginSpec = plugin {
      dependsIntellijModulesLang()
      extensions("""<projectService serviceImplementation="${UnloadedSettingsService::class.java.name}" configurationSchemaKey="unloadTest"/>""")
    }
    val pluginDisposable = loadPluginWithText(pluginSpec, FileUtil.createTempDirectory("plugins", null, true).toPath())
    try {
      project.runInLoadComponentStateMode { project.service<UnloadedSettingsService>() }
    }
    finally {
      Disposer.dispose(pluginDisposable)
    }

    LeakHunter.checkLeak(ConfigurationFileManager.getInstance(project), ReadOnlyStorage::class.java) {
      it.componentClass == UnloadedSettingsService::class.java
    }
  }
}

class UnloadedSettingsState : BaseState() {
  @Suppress("unused")
  var value by string()
}

class UnloadedSettingsService : SimplePersistentStateComponent<UnloadedSettingsState>(UnloadedSettingsState())

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.*
import javax.swing.Icon


class TestBaseService : SettingsService {
  companion object {
    private val LOG = logger<TestBaseService>()

    val IMPORT_SERVICE = "ImportService"
    fun getInstance() = service<TestBaseService>()
  }

  override fun getSyncService(): SyncService {
    return TestSyncService()
  }

  override fun getJbService(): JbService {
    return TestJbService()
  }

  override fun getExternalService(): ExternalService {
    return TestExternalService()
  }

  override fun skipImport() {
    LOG.info("$IMPORT_SERVICE skipImport")
  }
}

class TestJbService : JbService {
  companion object {

    private val LOG = logger<TestJbService>()

    val main = TestProduct("IdeaMain1", "версия", Date())
    val main2 = TestProduct("IdeaMain1", "версия", Date())
    val enemy = TestProduct("IdeaMain1", "версия", Date())

    val fresh = listOf(TestProduct("Idea111", "версия", Date()),
                       TestProduct("Idea222", "версия", Date()),
                       TestProduct("Idea333", "версия", Date()),
                       TestProduct("Idea444", "версия", Date()),
                       TestProduct("Idea555", "версия", Date()),
                       TestProduct("Idea666", "версия", Date()))

    val old = listOf(TestProduct("Idea111", "версия", Date()),
                     TestProduct("Idea222", "версия", Date()),
                     TestProduct("Idea333", "версия", Date()),
                     TestProduct("Idea444", "версия", Date()),
                     TestProduct("Idea555", "версия", Date()),
                     TestProduct("Idea666", "версия", Date()))

    val productList3 = listOf(TestProduct("Idea111", "версия", Date()),
                              TestProduct("Idea222", "версия", Date()))

    val children = listOf(
      listOf(TestChildrenSettings("Go to Everything", "built-in", "⌘T"),
             TestChildrenSettings("Find Usages", null, "⇧F12"),
             TestChildrenSettings("Build Solution", null, "⇧F12"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12")),
      listOf(TestChildrenSettings("Go to Everything"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12")),
      listOf(TestChildrenSettings("Go to Everything", "built-in", "⌘T"),
             TestChildrenSettings("Find Usages", null, "⇧F12"),
             TestChildrenSettings("Build Solution", null, "⇧F12"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12")),
    )

    val settings = listOf(
      TestBaseSetting(AllIcons.General.ExternalTools, "UI Theme", "Light Theme"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins",
                              "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", children),
      TestBaseSetting(AllIcons.General.ExternalTools, "Code settings", "Сode style, file types, live templates"),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins",
                              "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestBaseSetting(AllIcons.General.ExternalTools, "Code settings", "Сode style, file types, live templates"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Plugins",
                          "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", children),

      )

    val testChildConfig = TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins",
                                                  "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more",
                                                  children)

    fun getProductIcon(size: IconProductSize): Icon {
      return when (size) {
        IconProductSize.SMALL -> AllIcons.TransferSettings.Resharper
        IconProductSize.MIDDLE -> AllIcons.General.NotificationInfo
        IconProductSize.LARGE -> AllIcons.General.SuccessLogin
      }
    }

  }

  override fun importSettings(productId: String, data: List<DataForSave>) {
    LOG.info("${TestBaseService.IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
  }

  override fun products(): List<Product> {
    return productList3 //fresh
  }

  override fun getOldProducts(): List<Product> {
    return old
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    return settings
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon {
    return getProductIcon(size)
  }

  override fun getConfig(): Config {
    return object : Config {
      override val path: String = "/IntelliJ IDEA Ultimate 2023.2.1"

      override val id: String = "Config"
      override val name: String = "Config or Installation Directory"
    }
  }
}

class TestExternalService : ExternalService {
  companion object {
    private val LOG = logger<TestExternalService>()
  }

  override fun products(): List<Product> {
    return listOf(TestJbService.main2)
  }


  override fun getProductIcon(itemId: String, size: IconProductSize): Icon {
    return TestJbService.getProductIcon(size)
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    return TestJbService.settings
  }

  override fun importSettings(productId: String, data: List<DataForSave>) {
    LOG.info("${TestBaseService.IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
  }
}

class TestSyncService : SyncService {
  companion object {
    private val LOG = logger<TestSyncService>()
  }

  override val syncState: SyncService.SYNC_STATE
    get() = SyncService.SYNC_STATE.LOGGED


  override fun tryToLogin(): String? {
    LOG.info("${TestBaseService.IMPORT_SERVICE} tryToLogin")
    return null
  }

  override fun syncSettings(productId: String) {
    LOG.info("${TestBaseService.IMPORT_SERVICE} syncSettings id: '$productId' ")
  }

  override fun generalSync() {
    LOG.info("${TestBaseService.IMPORT_SERVICE} generalSync")
  }

  override fun getMainProduct(): Product? {
    return TestJbService.main2
  }

  override fun importSettings(productId: String) {
    LOG.info("${TestBaseService.IMPORT_SERVICE} importSettings product: $productId")
  }

  override fun products(): List<Product> {
    return TestJbService.fresh
  }

  override fun getOldProducts(): List<Product> {
    return TestJbService.old
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    return TestJbService.settings
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon {
    return AllIcons.Actions.Refresh
  }

}

class TestProduct(
  override val name: String,
  override val version: String,
  override val lastUsage: Date,
  override val id: String = UUID.randomUUID().toString()) : Product {

}


class TestBaseSetting(override val icon: Icon,
                      override val name: String,
                      override val comment: String?,
                      override val id: String = UUID.randomUUID().toString()) : BaseSetting

class TestMultipleSetting(override val icon: Icon,
                          override val name: String,
                          override val comment: String?,
                          override val list: List<List<ChildSetting>>,
                          override val id: String = UUID.randomUUID().toString()) : Multiple

class TestConfigurableSetting(override val icon: Icon,
                              override val name: String,
                              override val comment: String?,
                              override val list: List<List<ChildSetting>>,
                              override val id: String = UUID.randomUUID().toString()) : Configurable

class TestChildrenSettings(override val name: String,
                           override val leftComment: String? = null,
                           override val rightComment: String? = null,
                           override val id: String = UUID.randomUUID().toString()) : ChildSetting

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import javax.swing.Icon

internal const val IMPORT_SERVICE = "ImportService"

class TestJbService : JbService {
  companion object {

    private val LOG = logger<TestJbService>()

    val main = TestProduct("Main", "версия", Date(), "main")
    val main2 = TestProduct("IdeaMain1", "версия", Date())
    val enemy = TestProduct("IdeaMain1", "версия", Date())

    val fresh = listOf(main,
                       TestProduct("Idea222", "версия", Date()),
                       TestProduct("Idea333", "версия", Date()),
                       TestProduct("Idea444", "версия", Date()),
                       TestProduct("Idea555", "версия", Date()),
                       TestProduct("Idea666", "версия", Date()))
    val empty = emptyList<Product>()
    val old = listOf(
      TestProduct("Idea222", "версия", Date()),
      TestProduct("Idea333", "версия", Date()),
      TestProduct("Idea444", "версия", Date()),
      TestProduct("Idea555", "версия", Date()),
      TestProduct("Idea666", "версия", Date()))


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
    private val from = TestProduct("Visual Studio Code", "версия", Date())
    private val to = TestProduct("IntelliJ IDEA", "версия", Date())

    val progress = TestImportProgress(Lifetime.Eternal)

    val importFromProduct = TestImportFromProduct(DialogImportItem(from, AllIcons.TransferSettings.VS),
                                                  DialogImportItem(to, AllIcons.TransferSettings.Xcode), progress)

    val importFromProduct_ = TestImportFromProduct(DialogImportItem(from, AllIcons.TransferSettings.VS),
                                                   DialogImportItem(to, AllIcons.TransferSettings.Xcode), progress, null)


    val simpleImport = TestSimpleImport("From Config or Installation Directory", progress)

    fun getProductIcon(size: IconProductSize): Icon {
      return when (size) {
        IconProductSize.SMALL -> AllIcons.TransferSettings.Resharper
        IconProductSize.MIDDLE -> AllIcons.General.NotificationInfo
        IconProductSize.LARGE -> AllIcons.General.SuccessLogin
      }
    }
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    LOG.info("${IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
    return if (productId == getConfig().id) simpleImport else importFromProduct
  }

  override fun products(): List<Product> {
    return fresh
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

  override fun baseProduct(id: String): Boolean {
    return id == main.id
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

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    LOG.info("${IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
    return TestJbService.simpleImport
  }
}

class TestSyncService : SyncService {
  companion object {
    private val LOG = logger<TestSyncService>()
  }

  override fun baseProduct(id: String): Boolean {
    return id == TestJbService.main.id
  }

  override val syncState: IProperty<SyncService.SYNC_STATE> = Property(SyncService.SYNC_STATE.LOGGED)

  override fun tryToLogin(): String? {
    LOG.info("${IMPORT_SERVICE} tryToLogin")
    return null
  }

  override fun syncSettings(): DialogImportData {
    LOG.info("${IMPORT_SERVICE} syncSettings")
    return TestJbService.importFromProduct
  }

  override fun importSyncSettings(): DialogImportData {
    LOG.info("${IMPORT_SERVICE} importSettings")
    return TestJbService.importFromProduct
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    LOG.info("${IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
    return TestJbService.importFromProduct
  }

  override fun generalSync() {
    LOG.info("${IMPORT_SERVICE} generalSync")
  }

  override fun getMainProduct(): Product? {
    return TestJbService.main
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

class TestSimpleImport(override val message: String, override val progress: ImportProgress) : DialogImportData

class TestImportFromProduct(
  override val from: DialogImportItem,
  override val to: DialogImportItem,
  override val progress: ImportProgress, override val message: String? = "From ${from.item.name}") : ImportFromProduct

class TestImportProgress(lifetime: Lifetime) : ImportProgress {
  override val progressMessage = Property<String?>(null)
  override val progress = OptProperty<Int>()

  private var value: Int = 0
  private val list = listOf("Plugins: Docker", "Connect to WebApp", "Connect", "Show configuration on toolbar")
  private var index = 0

  init {
    lifetime.launch {
      launch(Dispatchers.Default) {
        while (true) {
          value = if (value < 98) value + 1 else 0
          progress.set(value)

          delay(1000L)
        }
      }

      launch(Dispatchers.Default) {
        while (true) {
          progressMessage.set(if (Math.random() < 0.5) {
            index = if (index < list.size - 1) index + 1 else 0
            list[index]
          }
                              else {
            null
          })

          delay(300L)
        }
      }
    }
  }
}
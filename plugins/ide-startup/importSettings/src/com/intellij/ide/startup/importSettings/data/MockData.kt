// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.LocalDate
import java.util.*
import javax.swing.Icon
import kotlin.random.Random

internal const val IMPORT_SERVICE = "ImportService"

class TestJbService private constructor(): JbService {
  companion object {
    private val service = TestJbService()

    fun getInstance() = service

    private val LOG = logger<TestJbService>()

    val dM1 = LocalDate.now().minusMonths(1)
    val dM6 = LocalDate.now().minusMonths(6)
    val dD6 = LocalDate.now().minusDays(6)
    val dD1 = LocalDate.now().minusDays(1)
    val dY1 = LocalDate.now().minusYears(1)
    val dY3 = LocalDate.now().minusYears(3)
    val dd20 = LocalDate.now().minusDays(20)
    val dd5 = LocalDate.now().minusDays(5)
    val dd2 = LocalDate.now().minusDays(2)


    val main3 = TestProduct("Main", "версия", dM1, "main")
    val main2 = TestProduct("IdeaMain1", "версия", LocalDate.now())
    val main4 = TestProduct("PyCharm Professional 2023.3.3-233.666", "верс 2023.3.3-233.666", LocalDate.now())
    val main = TestProduct("PyCharm Professional", "верс 2023.3.3-233.666", LocalDate.now())

    val fresh = listOf(
      main,
      main3,
      TestProduct("Idea222", "верс 2023.1-233.111", dM1),
      TestProduct("Idea333", "верс 2023.2.2-233.222", dM6),
      TestProduct("Idea444", "верс 2023.3.3-233.333", dD6),
      TestProduct("Idea555", "верс 2023.4.3-233.444", dD1),
      TestProduct("Idea666", "верс 2023.5.3-233.555", dY1),
      TestProduct("Idea666", "верс 2023.6.3-233.666", dY3),
      TestProduct("Idea666", "верс 2023.7.3-233.777", dd20),
      TestProduct("Idea666", "верс 2023.8.3-233.888", dd5),
      TestProduct("Idea666", "верс 2023.9.3-233.999", dd2),
    )
    val old = listOf(
      main4,
      TestProduct("Idea222", "верс 2023.1-233.111", LocalDate.now()),
      TestProduct("Idea333", "верс 2023.2.2-233.222", LocalDate.now()),
      TestProduct("Idea444", "верс 2023.3.2-233.333", LocalDate.now()),
      TestProduct("Idea555", "верс 2023.4.2-233.444", LocalDate.now()),
      TestProduct("Idea666", "верс 2023.5.2-233.555", LocalDate.now()))


    val children = listOf(
      listOf(TestChildrenSettings("Go to Everything", "built-in", "⌘T"),
             TestChildrenSettings("Find Usages", null, "⇧F12"),
             TestChildrenSettings("Build Solution", null, "⇧F12"),
             TestChildrenSettings("Go to Everything", "built-in", "⇧F12")),
      listOf(TestChildrenSettings("Go to Everything Everything"),
             TestChildrenSettings("Go to Every thing", "built-in", "⇧F12"),
             TestChildrenSettings("Go to Everyt Everything", "built-in", "⇧F12"),
             TestChildrenSettings("Go to Everything Go to Everything", "built-in", "⇧F12")),
      listOf(TestChildrenSettings("1 Go to Everything Go to Everything Go to Everything Everyth iBBngEverything Go to Everything Everyth ingEverything 111", "built-in", "⌘T"),
             TestChildrenSettings("Find Usages", null, "⇧F12"),
             TestChildrenSettings("Build Solution", null, "⇧F12"),
             TestChildrenSettings("Go", "built-in", "⇧F12")),
    )

    val oneChildren = listOf( listOf( TestChildrenSettings ("Go to EverythingEve rything")))

    val children1 = listOf(
      listOf(TestChildrenSettings("Go to EverythingEve rything"),
             TestChildrenSettings("Find ges"),
             TestChildrenSettings("Build Solution"),
             TestChildrenSettings("Go to Everything Go to Everything Go to Everything")),
      listOf(TestChildrenSettings("Go to Ev"),
             TestChildrenSettings("Go to Everything Go to Everything"),
             TestChildrenSettings("2 Go to Everythi Go to Everything Go to Everything 2222 Everyth ingEverything Go to Everything Everyth ingEverything 222"),
             TestChildrenSettings("Go to Everything")),
      listOf(TestChildrenSettings("Go to rything rything"),
             TestChildrenSettings("Find Usages"),
             TestChildrenSettings("Build Solution"),
             TestChildrenSettings("Go")),
    )

    val children2 = listOf(
      listOf(TestChildrenSettings("Go to Everything"),
             TestChildrenSettings("Find Usages"),
             TestChildrenSettings("Build Solution"),
             TestChildrenSettings("Go to Everything")),
      listOf(TestChildrenSettings("Go to Eveing"),
             TestChildrenSettings("3 Go to Everything"),
             TestChildrenSettings("Go to Everything Everything"),
             TestChildrenSettings("Go to ")),
      listOf(TestChildrenSettings("Go to Everything Everyth ingEverything"),
             TestChildrenSettings("Find Usages Go to"),
             TestChildrenSettings("Build Solution"),
             TestChildrenSettings("Go to Everything")),
    )

    val settings1 = listOf(
      TestBaseSetting(AllIcons.General.ExternalTools, "UI Theme", "Light Theme"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children1),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugin_", list = oneChildren),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins", list = emptyList()),

      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins", list = children),
    )

    val settings = listOf(
      TestBaseSetting(AllIcons.General.ExternalTools, "UI Theme", "Light Theme"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children1),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins", list = children),
      TestBaseSetting(AllIcons.General.ExternalTools, "Code settings", "Сode style, file types, live templates"),

      TestBaseSetting(AllIcons.General.ExternalTools, "UI Theme", "Light Theme"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children2),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugin_",
                              "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", oneChildren),
      TestBaseSetting(AllIcons.General.ExternalTools, "Code settings", "Сode style, file types, live templates"),
      TestConfigurableSetting(AllIcons.General.ExternalTools, "Plugins",
                              "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children1),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children2),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Keymap", "macOS, 12 custom keys", children1),
      TestBaseSetting(AllIcons.General.ExternalTools, "Code settings", "Сode style, file types, live templates"),
      TestMultipleSetting(AllIcons.General.ExternalTools, "Plugins",
                          "Grazie Pro, IdeaVim, JetBrains Academy, Solarized Theme, Gradianto, Nord, +3 more", children),
    )


    private val from = TestProduct("Visual Studio Code", "версия", LocalDate.now())
    private val to = TestProduct("IntelliJ IDEA", "версия", LocalDate.now())

    val progress = TestImportProgress(Lifetime.Eternal)

    val list = listOf(
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.AC_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.AC_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.AC_48),
      listOf(
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_20,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_24,
        com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_48),
    )

    val icon
      get() = list.get(Random.nextInt(list.size - 1))[2]
    val importFromProduct = TestImportFromProduct(DialogImportItem(from, icon),
                                                  DialogImportItem(to, icon), progress)


    val simpleImport = TestSimpleImport("From Config or Installation Directory", progress)


    private val map = mutableMapOf<String, List<Icon>>()

    fun getProductIcon(itemId: String, size: IconProductSize): Icon {
      val icons = map.getOrPut(itemId) { list[Random.nextInt(list.size - 1)] }

      return when (size) {
        IconProductSize.SMALL -> icons.get(0)
        IconProductSize.MIDDLE -> icons.get(1)
        IconProductSize.LARGE -> icons.get(2)
      }
    }
  }
  fun configChosen() {
    OnboardingController.getInstance().startWizard(isModal = false)
  }

  override suspend fun hasDataToImport() = true
  override suspend fun warmUp() {
    TODO("Not yet implemented")
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    LOG.info("${IMPORT_SERVICE} importSettings product: $productId data: ${data.size}")
    return importFromProduct
  }

  override fun products(): List<Product> {
    return fresh
  }

  override fun getOldProducts(): List<Product> {
    return old
  }

  override fun importFromCustomFolder(folderPath: Path) {
    TODO("Not yet implemented")
  }


  override fun getSettings(itemId: String): List<BaseSetting> {
    return if (itemId == main.id) settings1 else settings
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon {
    return TestJbService.getProductIcon(itemId, size)
  }

  override fun baseProduct(id: String): Boolean {
    return id == main.id
  }
}

class TestExternalService : ExternalService {

  override suspend fun hasDataToImport() = true

  override fun warmUp(scope: CoroutineScope) {}

  override val productServices: List<ExternalProductService> = listOf(TestExternalProductService())
}

class TestExternalProductService : ExternalProductService {

  companion object {
    private val LOG = logger<TestExternalService>()
  }

  override val productId: TransferableIdeId
    get() = TransferableIdeId.VSCode
  override val productTitle: String
    get() = ""

  override fun products(): List<Product> {
    return listOf(TestJbService.main2)
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon {
    return TestJbService.getProductIcon(itemId, size)
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

  override suspend fun hasDataToImport() = true
  override suspend fun warmUp() {
    TODO("Not yet implemented")
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

  override fun getMainProduct(): Product {
    return TestJbService.main
  }

  override fun products(): List<Product> {
    return TestJbService.fresh
  }

  override fun getOldProducts(): List<Product> {
    return TestJbService.old
  }

  override fun importFromCustomFolder(folderPath: Path) {
    TODO("Not yet implemented")
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
  override val lastUsage: LocalDate,
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
                              override val comment: String? = null,
                              override val list: List<List<ChildSetting>>,
                              override val id: String = UUID.randomUUID().toString()) : Configurable

class TestChildrenSettings(override val name: String,
                           override val leftComment: String? = null,
                           override val rightComment: String? = null,
                           override val id: String = UUID.randomUUID().toString()) : ChildSetting

class TestSimpleImport(override val message: String, override val progress: ImportProgress) : DialogImportData

class TestImportFromProduct(
  override val  from: DialogImportItem,
  override val to: DialogImportItem,
  override val progress: ImportProgress, override val message: String? = "From ${from.item.name}") : ImportFromProduct

open class TestImportProgress(lifetime: Lifetime) : ImportProgress {
  override val progressMessage = Property<String?>(null)
  override val progress = OptProperty<Int>()

  private var value: Int = 0
  private val list = listOf("Plugins: Docker", "Show configuration on toolbar", "Connect to WebApp",
                            "Show configuration on toolbar Show configuration on toolbar", "Connect", "Show configuration on toolbar")
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
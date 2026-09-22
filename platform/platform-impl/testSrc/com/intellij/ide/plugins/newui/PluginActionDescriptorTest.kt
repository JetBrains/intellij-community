// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable.TopComponentController
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.swing.JComponent

@TestApplication
internal class PluginActionDescriptorTest {
  @Test
  fun `model builders use the same details loaded calculation`() {
    fun buildModel(
      factory: PluginUiModelBuilderFactory,
      externalPluginId: String?,
      externalUpdateId: String?,
      description: String?,
    ): PluginUiModel {
      return factory.createBuilder(PluginId.getId("details.loaded.test"))
        .setExternalPluginId(externalPluginId)
        .setExternalUpdateId(externalUpdateId)
        .setDescription(description)
        .build()
    }

    for (factory in listOf(PluginNodeModelBuilderFactory, PluginDtoModelBuilderFactory)) {
      val factoryName = factory.javaClass.simpleName
      assertThat(buildModel(factory, "100", "200", null).detailsLoaded).describedAs(factoryName).isFalse()
      assertThat(buildModel(factory, "100", "200", "Complete details").detailsLoaded).describedAs(factoryName).isTrue()
      assertThat(buildModel(factory, null, "200", null).detailsLoaded).describedAs(factoryName).isTrue()
      assertThat(buildModel(factory, "100", null, null).detailsLoaded).describedAs(factoryName).isTrue()
    }
  }

  @Test
  @Timeout(30)
  fun `install loads incomplete Marketplace details before the operation`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val pluginId = PluginId.getId("incomplete.marketplace.plugin")
    val incomplete = PluginNodeModelBuilderFactory.createBuilder(pluginId)
      .setName("Incomplete Marketplace Plugin")
      .setExternalPluginId("100")
      .setExternalUpdateId("200")
      .build()
    val complete = PluginNodeModelBuilderFactory.createBuilder(pluginId)
      .setName("Complete Marketplace Plugin")
      .setDescription("Complete details")
      .setVendor("JetBrains")
      .setExternalPluginId("100")
      .setExternalUpdateId("200")
      .build()
    val controller = RecordingController(complete)
    val pluginModel = createPluginModel()
    TestDialogManager.setTestDialog(TestDialog { throw AssertionError("No third-party plugin dialog expected") }, disposable)

    try {
      assertThat(incomplete.detailsLoaded).isFalse()
      assertThat(PluginManagerCore.isVendorTrusted(incomplete.getDescriptor())).isFalse()

      val result = pluginModel.installOrUpdatePlugin(
        PluginOperationUiContext.withoutParent(ModalityState.any()),
        incomplete,
        null,
        this,
        controller,
      )

      assertThat(result).isSameAs(controller.installResult)
      assertThat(PluginManagerCore.isVendorTrusted(complete.getDescriptor())).isTrue()
      assertThat(controller.loadedModels).containsExactly(incomplete)
      assertThat(controller.installationRequests).containsExactly(complete to null)
    }
    finally {
      closePluginModel(pluginModel, complete)
    }
  }

  @Test
  @Timeout(30)
  fun `action reuses complete Marketplace details`(): Unit = timeoutRunBlocking {
    val complete = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("complete.marketplace.plugin"))
      .setName("Complete Marketplace Plugin")
      .setDescription("Complete details")
      .setVendor("JetBrains")
      .setExternalPluginId("300")
      .setExternalUpdateId("400")
      .build()
    var loadRequested = false
    val controller = object : UiPluginManagerController by DefaultUiPluginManagerController {
      override suspend fun loadPluginDetails(model: PluginUiModel): PluginUiModel {
        loadRequested = true
        return model
      }
    }

    assertThat(complete.detailsLoaded).isTrue()
    assertThat(loadPluginActionDescriptor(complete, controller)).isSameAs(complete)
    assertThat(loadRequested).isFalse()
  }

  @Test
  @Timeout(30)
  fun `install reports when Marketplace details cannot load`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val incomplete = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("unavailable.marketplace.plugin"))
      .setName("Unavailable Marketplace Plugin")
      .setExternalPluginId("500")
      .setExternalUpdateId("600")
      .build()
    val controller = RecordingController(null)
    val pluginModel = createPluginModel()
    val dialogMessages = mutableListOf<String>()
    TestDialogManager.setTestDialog(TestDialog { message ->
      dialogMessages.add(message)
      Messages.OK
    }, disposable)

    try {
      val result = pluginModel.installOrUpdatePlugin(
        PluginOperationUiContext.withoutParent(ModalityState.any()),
        incomplete,
        null,
        this,
        controller,
      )

      assertThat(result).isNull()
      assertThat(controller.loadedModels).containsExactly(incomplete)
      assertThat(controller.installationRequests).isEmpty()
      assertThat(dialogMessages).containsExactly(
        IdeBundle.message("plugins.configurable.plugin.details.loading.failed", incomplete.name)
      )
    }
    finally {
      closePluginModel(pluginModel, incomplete)
    }
  }

  @Test
  @Timeout(30)
  fun `install reports when loaded Marketplace details remain incomplete`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val incomplete = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("still.incomplete.marketplace.plugin"))
      .setName("Still Incomplete Marketplace Plugin")
      .setExternalPluginId("700")
      .setExternalUpdateId("800")
      .build()
    val controller = RecordingController(incomplete)
    val pluginModel = createPluginModel()
    val dialogMessages = mutableListOf<String>()
    TestDialogManager.setTestDialog(TestDialog { message ->
      dialogMessages.add(message)
      Messages.OK
    }, disposable)

    try {
      val result = pluginModel.installOrUpdatePlugin(
        PluginOperationUiContext.withoutParent(ModalityState.any()),
        incomplete,
        null,
        this,
        controller,
      )

      assertThat(result).isNull()
      assertThat(controller.loadedModels).containsExactly(incomplete)
      assertThat(controller.installationRequests).isEmpty()
      assertThat(dialogMessages).containsExactly(
        IdeBundle.message("plugins.configurable.plugin.details.loading.failed", incomplete.name)
      )
    }
    finally {
      closePluginModel(pluginModel, incomplete)
    }
  }

  private suspend fun createPluginModel(): MyPluginModel {
    return withContext(Dispatchers.UiWithModelAccess) {
      MyPluginModel(null).apply {
        setTopController(TopComponentController.EMPTY)
      }
    }
  }

  private suspend fun closePluginModel(pluginModel: MyPluginModel, actionDescriptor: PluginUiModel) {
    MyPluginModel.finishInstallation(actionDescriptor)
    DefaultUiPluginManagerController.closeSession(pluginModel.sessionId)
  }

  private class RecordingController(
    private val details: PluginUiModel?,
  ) : UiPluginManagerController by DefaultUiPluginManagerController {
    val installResult = InstallPluginResult().apply {
      success = false
      showErrors = false
      restartRequired = false
    }
    val loadedModels = mutableListOf<PluginUiModel>()
    val installationRequests = mutableListOf<Pair<PluginUiModel, PluginUiModel?>>()

    override suspend fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
      loadedModels.add(model)
      return details
    }

    override suspend fun installOrUpdatePlugin(
      sessionId: String,
      parentComponent: () -> JComponent?,
      descriptor: PluginUiModel,
      updateDescriptor: PluginUiModel?,
      installSource: FUSEventSource?,
      modalityState: ModalityState?,
      pluginEnabler: PluginEnabler?,
      customRepoPlugins: List<PluginUiModel>?,
      progressSink: PluginInstallationProgressSink,
    ): InstallPluginResult {
      installationRequests.add(descriptor to updateDescriptor)
      return installResult
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.ide.util.AppPropertyService
import com.intellij.ide.util.BasePropertyService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.fileSizeSafe
import com.intellij.util.xmlb.JdomAdapter
import org.jdom.Element
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile

internal object JbImportSpecialHandler {
  private val specialHandlers = listOf<JbImportXmlHandler>(OtherXmlHandler)

  fun postProcess(prevConfigDir: Path) {
    for (handler in specialHandlers) {
      try {
        handler.process(prevConfigDir)
      }
      catch (th: Throwable) {
        logger.warn("error during special import handler ${handler.name}", th)
      }
    }
  }
}

internal interface JbImportXmlHandler {
  val name: String
  fun process(configDir: Path)
}

private object OtherXmlHandler : JbImportXmlHandler {
  private const val MAX_FILE_SIZE = 16 * 1024 * 1024L // 16MB
  override val name = "OtherXmlHandler"

  override fun process(configDir: Path) {
    val otherXmlFile = configDir / PathManager.OPTIONS_DIRECTORY / StoragePathMacros.NON_ROAMABLE_FILE
    if (!otherXmlFile.isRegularFile() || otherXmlFile.fileSizeSafe(MAX_FILE_SIZE) >= MAX_FILE_SIZE) {
      logger.warn("Won't process handler $name, because the file is missing or too large")
      return
    }
    val otherXmlDocument = JDOMUtil.load(otherXmlFile)
    // process DFM MODE
    for (component in otherXmlDocument.children) {
      if (component.getAttributeValue("name") == AppPropertyService.COMPONENT_NAME) {
        processDFMInPropertiesService(component)
      }
    }

    processExperimentalToolbar()
  }

  // IDEA-344035
  fun processDFMInPropertiesService(componentElement: Element) {
    val propertiesService = PropertiesComponent.getInstance()
    val extState = jdomSerializer.deserialize(componentElement, BasePropertyService.MyState::class.java, JdomAdapter)

    extState.keyToString.forEach { (key, value) ->
      if (key.contains(DistractionFreeModeController.DISTRACTION_MODE_PROPERTY_KEY)) {
        propertiesService.setValue(key, value)
      }
    }
  }

  fun processExperimentalToolbar() {
    if (NotRoamableUiSettings.getInstance().experimentalSingleStripe) {
      NotRoamableUiSettings.getInstance().experimentalSingleStripe = false
    }
  }
}

private val logger = logger<JbImportSpecialHandler>()

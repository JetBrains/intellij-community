// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.configurationStore.CURRENT_NAME_CONVERTER
import com.intellij.configurationStore.SchemeNameToFileName
import com.intellij.configurationStore.StreamProvider
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jdom.Parent
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface ExternalizableScheme : Scheme {
  fun setName(value: String)
}

abstract class SchemeManagerFactory(protected open val project: Project? = null) {
  companion object {
    @JvmStatic
    fun getInstance(): SchemeManagerFactory = service<SchemeManagerFactory>()

    @JvmStatic
    fun getInstance(project: Project): SchemeManagerFactory = project.service<SchemeManagerFactory>()
  }

  /**
   * directoryName - like "keymaps".
   */
  @JvmOverloads
  fun <SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> create(
    directoryName: String,
    processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>,
    presentableName: String? = null,
    directoryPath: Path? = null,
    settingsCategory: SettingsCategory = SettingsCategory.OTHER,
  ): SchemeManager<SCHEME> {
    return create(
      directoryName = directoryName,
      processor = processor,
      presentableName = presentableName,
      roamingType = RoamingType.DEFAULT,
      directoryPath = directoryPath,
      settingsCategory = settingsCategory,
    )
  }

  @ApiStatus.Internal
  abstract fun <SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> create(
    directoryName: String,
    processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>,
    presentableName: String? = null,
    roamingType: RoamingType = RoamingType.DEFAULT,
    schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
    streamProvider: StreamProvider? = null,
    directoryPath: Path? = null,
    isAutoSave: Boolean = true,
    settingsCategory: SettingsCategory = SettingsCategory.OTHER
  ): SchemeManager<SCHEME>

  open fun dispose(schemeManager: SchemeManager<*>) {}
}

enum class SchemeState {
  UNCHANGED, NON_PERSISTENT, POSSIBLY_CHANGED
}

abstract class SchemeProcessor<SCHEME: Scheme, in MUTABLE_SCHEME: SCHEME> {
  open fun getSchemeKey(scheme: SCHEME): String = scheme.name

  open fun isExternalizable(scheme: SCHEME): Boolean = scheme is ExternalizableScheme

  /**
   * Element will not be modified, it is safe to return non-cloned instance.
   */
  abstract fun writeScheme(scheme: MUTABLE_SCHEME): Parent?

  /**
   * Called on an external scheme add or change file events.
   */
  open fun onSchemeAdded(scheme: MUTABLE_SCHEME) { }

  open fun onSchemeDeleted(scheme: MUTABLE_SCHEME) { }

  open fun onCurrentSchemeSwitched(oldScheme: SCHEME?, newScheme: SCHEME?, processChangeSynchronously: Boolean) { }

  /**
   * If scheme implements [com.intellij.configurationStore.SerializableScheme],
   * this method will be called only if [com.intellij.configurationStore.SerializableScheme.getSchemeState] returns `null`.
   */
  open fun getState(scheme: SCHEME): SchemeState = SchemeState.POSSIBLY_CHANGED

  /**
   * May be called from any thread - EDT is not guaranteed.
   */
  open fun beforeReloaded(schemeManager: SchemeManager<SCHEME>) { }

  /**
   * May be called from any thread - EDT is not guaranteed.
   */
  open fun reloaded(schemeManager: SchemeManager<SCHEME>, schemes: Collection<SCHEME>) { }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.configurationStore.CURRENT_NAME_CONVERTER
import com.intellij.configurationStore.SchemeNameToFileName
import com.intellij.configurationStore.StreamProvider
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jdom.Parent
import java.nio.file.Path

@Deprecated("Please use SchemeManager")
abstract class SchemesManager<T> : SchemeManager<T>()

interface ExternalizableScheme : Scheme {
  fun setName(value: String)
}

abstract class SchemeManagerFactory {
  companion object {
    @JvmStatic
    fun getInstance(): SchemeManagerFactory = ServiceManager.getService(SchemeManagerFactory::class.java)!!

    @JvmStatic
    fun getInstance(project: Project): SchemeManagerFactory = ServiceManager.getService(project, SchemeManagerFactory::class.java)!!
  }

  /**
   * directoryName - like "keymaps".
   */
  @JvmOverloads
  fun <SCHEME : Any, MUTABLE_SCHEME : SCHEME> create(directoryName: String, processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>, presentableName: String? = null, directoryPath: Path? = null): SchemeManager<SCHEME> {
    return create(directoryName, processor, presentableName, RoamingType.DEFAULT, directoryPath = directoryPath)
  }

  abstract fun <SCHEME : Any, MUTABLE_SCHEME : SCHEME> create(directoryName: String,
                                                              processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>,
                                                              presentableName: String? = null,
                                                              roamingType: RoamingType = RoamingType.DEFAULT,
                                                              schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
                                                              streamProvider: StreamProvider? = null,
                                                              directoryPath: Path? = null,
                                                              isAutoSave: Boolean = true): SchemeManager<SCHEME>

  open fun dispose(schemeManager: SchemeManager<*>) {
  }
}

enum class SchemeState {
  UNCHANGED, NON_PERSISTENT, POSSIBLY_CHANGED
}

abstract class SchemeProcessor<SCHEME, in MUTABLE_SCHEME: SCHEME> {
  open fun getSchemeKey(scheme: SCHEME): String {
    return (scheme as Scheme).name
  }

  open fun isExternalizable(scheme: SCHEME): Boolean = scheme is ExternalizableScheme

  /**
   * Element will not be modified, it is safe to return non-cloned instance.
   */
  abstract fun writeScheme(scheme: MUTABLE_SCHEME): Parent?

  open fun initScheme(scheme: MUTABLE_SCHEME) {
  }

  /**
   * Called on external scheme add or change file events.
   */
  open fun onSchemeAdded(scheme: MUTABLE_SCHEME) {
  }

  open fun onSchemeDeleted(scheme: MUTABLE_SCHEME) {
  }

  open fun onCurrentSchemeSwitched(oldScheme: SCHEME?, newScheme: SCHEME?) {
  }

  /**
   * If scheme implements [com.intellij.configurationStore.SerializableScheme], this method will be called only if [com.intellij.configurationStore.SerializableScheme.getSchemeState] returns `null`
   */
  @Suppress("KDocUnresolvedReference")
  open fun getState(scheme: SCHEME): SchemeState = SchemeState.POSSIBLY_CHANGED

  /**
   * May be called from any thread - EDT is not guaranteed.
   */
  open fun beforeReloaded(schemeManager: SchemeManager<SCHEME>) {
  }

  /**
   * May be called from any thread - EDT is not guaranteed.
   */
  open fun reloaded(schemeManager: SchemeManager<SCHEME>, schemes: Collection<SCHEME>) {
  }
}
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
abstract class SchemesManager<T : Scheme> : SchemeManager<T>()

interface ExternalizableScheme : Scheme {
  fun setName(value: String)
}

abstract class SchemeManagerFactory {
  companion object {
    @JvmStatic
    fun getInstance() = ServiceManager.getService(SchemeManagerFactory::class.java)!!

    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, SchemeManagerFactory::class.java)!!
  }

  /**
   * directoryName - like "keymaps".
   */
  @JvmOverloads
  fun <SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> create(directoryName: String, processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>, presentableName: String? = null, directoryPath: Path? = null): SchemeManager<SCHEME> {
    return create(directoryName, processor, presentableName, RoamingType.DEFAULT, directoryPath = directoryPath)
  }

  abstract fun <SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> create(directoryName: String,
                                                                 processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>,
                                                                 presentableName: String? = null,
                                                                 roamingType: RoamingType = RoamingType.DEFAULT,
                                                                 schemeNameToFileName: SchemeNameToFileName = CURRENT_NAME_CONVERTER,
                                                                 streamProvider: StreamProvider? = null,
                                                                 directoryPath: Path? = null,
                                                                 autoSave: Boolean = true): SchemeManager<SCHEME>
  open fun dispose(schemeManager: SchemeManager<*>) {
  }
}

enum class SchemeState {
  UNCHANGED, NON_PERSISTENT, POSSIBLY_CHANGED
}

abstract class SchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME: SCHEME> {
  open fun isExternalizable(scheme: SCHEME) = scheme is ExternalizableScheme

  /**
   * Element will not be modified, it is safe to return non-cloned instance.
   */
  abstract fun writeScheme(scheme: MUTABLE_SCHEME): Parent

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
  open fun getState(scheme: SCHEME) = SchemeState.POSSIBLY_CHANGED
}
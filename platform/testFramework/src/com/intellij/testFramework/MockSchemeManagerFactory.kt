// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.configurationStore.SchemeNameToFileName
import com.intellij.configurationStore.StreamProvider
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.options.*
import java.nio.file.Path

private val EMPTY = EmptySchemesManager()

class MockSchemeManagerFactory : SchemeManagerFactory() {
  override fun <SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> create(directoryName: String,
                                                                 processor: SchemeProcessor<SCHEME, MUTABLE_SCHEME>,
                                                                 presentableName: String?,
                                                                 roamingType: RoamingType,
                                                                 schemeNameToFileName: SchemeNameToFileName,
                                                                 streamProvider: StreamProvider?,
                                                                 directoryPath: Path?,
                                                                 isAutoSave: Boolean): SchemeManager<SCHEME> {
    @Suppress("UNCHECKED_CAST")
    return EMPTY as SchemeManager<SCHEME>
  }
}

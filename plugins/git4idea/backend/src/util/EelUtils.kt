// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking

object EelUtils {
  private val LOG = logger<EelUtils>()

  fun getEel(project: Project): EelApi? {
    return try {
      project.getEelDescriptor().toEelApiBlocking()
    }
    catch (e: EelUnavailableException) {
      LOG.warn("Could not connect to the project's Eel environment", e)
      null
    }
  }
}
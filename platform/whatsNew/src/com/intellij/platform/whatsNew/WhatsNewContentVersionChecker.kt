// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.logger

internal class WhatsNewContentVersionChecker {
  companion object {
    private val LOG = logger<WhatsNewContentVersionChecker>()
    private const val LAST_SHOWN_EAP_VERSION_PROP = "whats.new.last.shown.version"


    fun isNeedToShowContent(whatsNewContent: WhatsNewContent): Boolean {
      val savedVersionInfo = PropertiesComponent.getInstance().getValue(LAST_SHOWN_EAP_VERSION_PROP) ?: run {
        LOG.info("$LAST_SHOWN_EAP_VERSION_PROP is not defined. Will show What's New.")
        return true
      }
      val savedVersion = WhatsNewContent.ContentVersion.parse(savedVersionInfo) ?: run {
        LOG.warn("Cannot parse last shown What's New version: \"$savedVersionInfo\". Will show What's new as fallback.")
        return true
      }

      val newVersion = whatsNewContent.getVersion() ?: run {
        LOG.warn("What's New content provider returns null version. What's New will be ignored.")
        return false
      }

      val result = newVersion > savedVersion || (newVersion.releaseInfoEquals(savedVersion) && newVersion.hash != savedVersion.hash)
      LOG.info("Comparing versions $newVersion > $savedVersion: $result.")
      return result
    }

    fun saveLastShownContent(content: WhatsNewContent) {
      LOG.info("EapWhatsNew version saved '${content.getVersion()}'")
      val version = content.getVersion() ?: run {
        LOG.error("What's New content $content returned a null version.")
        return
      }

      PropertiesComponent.getInstance().setValue(LAST_SHOWN_EAP_VERSION_PROP, version.toString())
    }
  }
}
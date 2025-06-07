// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.nullize

internal class WhatsNewContentVersionChecker {
  companion object {
    private val LOG = logger<WhatsNewContentVersionChecker>()
    private const val LAST_SHOWN_EAP_VERSION_PROP = "whats.new.last.shown.version"


    fun isNeedToShowContent(whatsNewContent: WhatsNewContent): Boolean {
      val newVersion = whatsNewContent.getVersion() ?: run {
        LOG.info("What's New content provider returns null version. What's New will be ignored.")
        return false
      }

      val savedVersionInfo = PropertiesComponent.getInstance().getValue(LAST_SHOWN_EAP_VERSION_PROP) ?: run {
        LOG.info("$LAST_SHOWN_EAP_VERSION_PROP is not defined. Will show What's New.")
        return true
      }
      val savedVersion = WhatsNewContent.ContentVersion.parse(savedVersionInfo) ?: run {
        LOG.warn("Cannot parse last shown What's New version: \"$savedVersionInfo\". Will show What's new as fallback.")
        return true
      }

      val result = shouldShowWhatsNew(savedVersion, newVersion)
      LOG.info("Comparing versions $newVersion and $savedVersion: $result.")
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

    internal fun shouldShowWhatsNew(
      storedVersion: WhatsNewContent.ContentVersion,
      newVersion: WhatsNewContent.ContentVersion): Boolean {
      if (storedVersion.eap == null && newVersion.eap == null) {
        if (storedVersion.hash.nullize() == null || newVersion.hash.nullize() == null) {
          // Both versions are release ⇒ compare versions only.
          return newVersion > storedVersion
        }

        // Both versions are release but might have the same hash ⇒ compare versions and hash.
        return newVersion > storedVersion && storedVersion.hash != newVersion.hash
      }

      if (storedVersion.hash.nullize() != null && newVersion.hash.nullize() != null) {
        // If both versions have hashes, then show any new content (i.e., hashes are different and the version is new).
        return storedVersion.hash != newVersion.hash && newVersion >= storedVersion
      }

      // At least one of the versions doesn't have a hash: compare them by versions directly, preferring the newest.
      return newVersion > storedVersion
    }
  }
}
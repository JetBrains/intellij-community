package com.intellij.platform.whatsNew

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.customization.ExternalProductResourceUrls

class WhatsNewContentVersionChecker {
  companion object {
    private val LOG = logger<WhatsNewContentVersionChecker>()
    private const val LAST_SHOWN_EAP_URL_PROP = "whats.new.last.shown.url"
    private val linkRegEx = "^https://www\\.jetbrains\\.com/[a-zA-Z]+/whatsnew(-eap)?/(\\d+)-(\\d+)-(\\d+)/$".toRegex()

    fun getUrl(): String? {
      return ExternalProductResourceUrls.getInstance().whatIsNewPageUrl?.toDecodedForm()
    }

    fun productVersion(): ContentVersion? {
      return try {
        val year = ApplicationInfo.getInstance().majorVersion.toInt()
        val release = ApplicationInfo.getInstance().minorVersion.toInt()
        ContentVersion(year, release, 0)
      } catch (e: NumberFormatException) {
        LOG.warn("WhatsNew: unknown productVersion '$e'")
        null
      }
    }

    fun lastShownLinkVersion(): ContentVersion? {
      return PropertiesComponent.getInstance().getValue(LAST_SHOWN_EAP_URL_PROP)?.let {
        return parseUrl(it) ?: run {
          if (LOG.isTraceEnabled) {
            LOG.trace("WhatsNew: unknown lastShownLinkVersion: '$it'")
          }
          null
        }
      }
    }

    fun linkVersion(): ContentVersion? {
      val url = getUrl()
      if (url != null) {
        return parseUrl(url)
      }
      return null
    }

    fun saveLastShownUrl(url: String) {
      if (LOG.isTraceEnabled) {
        LOG.trace("EapWhatsNew URL saved '$url'")
      }
      PropertiesComponent.getInstance().setValue(LAST_SHOWN_EAP_URL_PROP, url)
    }


    private fun parseUrl(link: String): ContentVersion? {
      linkRegEx.matchEntire(link)?.let {
        val year = it.groups[it.groups.size - 3]?.value?.toInt() ?: return@let null
        val release = it.groups[it.groups.size - 2]?.value?.toInt() ?: return@let null
        val eap = it.groups[it.groups.size - 1]?.value?.toInt() ?: return@let null

        return ContentVersion(year, release, eap)
      } ?: run {
        if (LOG.isTraceEnabled) {
          LOG.trace("EapWhatsNew: incompatible link '$link'")
        }
      }
      return null
    }

    data class ContentVersion(val year: Int, val release: Int, val eap: Int)

  }
}
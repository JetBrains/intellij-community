// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.write
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

internal data class GpgAgentConfig(val path: Path, val content: Map<String, String>) {
  val pinentryProgram: String? get() = content[PINENTRY_PROGRAM]

  constructor(gpgAgentPaths: GpgAgentPaths, existingConfig: GpgAgentConfig? = null) :
    this(gpgAgentPaths.gpgAgentConf, buildMap {
      putAll(existingConfig?.content.orEmpty())

      this[DEFAULT_CACHE_TTL] = DEFAULT_CACHE_TTL_CONF_VALUE
      this[MAX_CACHE_TTL] = MAX_CACHE_TTL_CONF_VALUE
      this[PINENTRY_PROGRAM] = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
    })

  fun isIntellijPinentryConfigured(paths: GpgAgentPaths): Boolean =
    pinentryProgram == paths.gpgPinentryAppLauncherConfigPath

  @Throws(IOException::class)
  fun writeToFile() {
    LOG.info("Writing gpg agent config to ${path.toFile()}")
    path.writeLines(content.map { (key, value) -> "$key $value".trimEnd() })
  }

  companion object {
    const val PINENTRY_PROGRAM = "pinentry-program"
    const val DEFAULT_CACHE_TTL = "default-cache-ttl"
    const val MAX_CACHE_TTL = "max-cache-ttl"

    const val DEFAULT_CACHE_TTL_CONF_VALUE = "1800"
    const val MAX_CACHE_TTL_CONF_VALUE = "7200"

    private val LOG = thisLogger()

    fun readConfig(gpgAgentConf: Path): GpgAgentConfig? {
      if (!gpgAgentConf.exists()) {
        LOG.debug("Cannot find $gpgAgentConf")
        return null
      }

      try {
        val config = gpgAgentConf.readLines().filterNot { it.startsWith("#") }.associate { line ->
          val keyValue = line.split(' ', limit = 2)
          val key = keyValue[0]
          val value = keyValue.getOrElse(1) { "" }
          key to value
        }
        return GpgAgentConfig(gpgAgentConf, config)
      }
      catch (e: IOException) {
        LOG.warn("Failed to read $gpgAgentConf", e)
        throw ReadGpgAgentConfigException(e)
      }
    }
  }
}

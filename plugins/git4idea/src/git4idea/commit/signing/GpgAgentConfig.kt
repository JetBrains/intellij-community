// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

internal data class GpgAgentConfig(val path: Path, val content: Map<String, String>) {
  val pinentryProgram: String? get() = content[PINENTRY_PROGRAM]
  val pinentryProgramFallback: String? get() = content[PINENTRY_PROGRAM_FALLBACK]

  constructor(gpgAgentPaths: GpgAgentPaths, pinentryFallback: String, existingConfig: GpgAgentConfig? = null) :
    this(gpgAgentPaths.gpgAgentConf, buildMap {
      putAll(existingConfig?.content.orEmpty())

      this[DEFAULT_CACHE_TTL] = DEFAULT_CACHE_TTL_CONF_VALUE
      this[MAX_CACHE_TTL] = MAX_CACHE_TTL_CONF_VALUE
      this[PINENTRY_PROGRAM] = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
      this[PINENTRY_PROGRAM_FALLBACK] = pinentryFallback
    })

  fun isIntellijPinentryConfigured(paths: GpgAgentPaths): Boolean =
    pinentryProgram == paths.gpgPinentryAppLauncherConfigPath

  @Throws(IOException::class)
  fun writeToFile() {
    LOG.info("Writing gpg agent config to ${path.toFile()}")
    FileUtil.writeToFile(path.toFile(),
                         content.map { (key, value) -> "$key $value".trimEnd() }.joinToString(separator = "\n"))
  }

  companion object {
    const val PINENTRY_PROGRAM = "pinentry-program"
    const val PINENTRY_PROGRAM_FALLBACK = "pinentry-program-fallback"
    const val DEFAULT_CACHE_TTL = "default-cache-ttl"
    const val MAX_CACHE_TTL = "max-cache-ttl"

    const val DEFAULT_CACHE_TTL_CONF_VALUE = "1800"
    const val MAX_CACHE_TTL_CONF_VALUE = "7200"

    private val LOG = thisLogger()

    fun readConfig(gpgAgentConf: Path): Result<GpgAgentConfig?> {
      if (!gpgAgentConf.exists()) {
        LOG.debug("Cannot find $gpgAgentConf")
        return Result.success(null)
      }
      val config = mutableMapOf<String, String>()
      return runCatching {
        for (line in gpgAgentConf.readLines()) {
          val keyValue = line.split(' ')
          if (keyValue.size > 2) continue
          val key = keyValue[0]
          val value = keyValue.getOrNull(1) ?: ""
          config[key] = value
        }
        GpgAgentConfig(gpgAgentConf, config)
      }
    }
  }
}

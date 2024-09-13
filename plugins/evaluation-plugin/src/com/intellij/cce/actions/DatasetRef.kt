package com.intellij.cce.actions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

sealed interface DatasetRef {

  val name: String

  fun prepare(datasetContext: DatasetContext)

  companion object {
    private const val EXISTING_PROTOCOL = "existing:"

    fun parse(ref: String): DatasetRef {
      if (ref.startsWith(EXISTING_PROTOCOL)) {
        return ExistingRef(ref.substring(EXISTING_PROTOCOL.length))
      }

      if (ref.contains(":")) {
        throw IllegalArgumentException("Protocol is not supported: $ref")
      }

      return ConfigRelativeRef(ref)
    }
  }
}

internal data class ConfigRelativeRef(val relativePath: String) : DatasetRef {
  override val name: String = Path.of(relativePath).normalize().toString()

  override fun prepare(datasetContext: DatasetContext) {
    val targetPath = datasetContext.path(this)

    if (targetPath.exists()) {
      return
    }

    val configPath = checkNotNull(datasetContext.configPath) {
      "Path $relativePath supposed to be relative to config, but there is no config explicitly provided. " +
      "Note that this option is only for test purposes and not supposed to be used in production."
    }

    val sourcePath = Path.of(configPath).parent.resolve(relativePath)

    check(sourcePath.exists()) {
      "Config-relative path $relativePath does not exist: $sourcePath"
    }

    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
  }
}

internal data class ExistingRef(override val name: String) : DatasetRef {
  override fun prepare(datasetContext: DatasetContext) {
    val path = datasetContext.path(name)

    check(path.exists()) {
      "Dataset $name does not exist: $path"
    }
  }
}
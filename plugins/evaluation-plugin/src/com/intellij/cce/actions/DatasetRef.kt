package com.intellij.cce.actions

import com.intellij.cce.util.httpGet
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists


sealed interface DatasetRef {

  val name: String

  fun prepare(datasetContext: DatasetContext)

  companion object {
    private const val EXISTING_PROTOCOL = "existing:"
    private const val REMOTE_PROTOCOL = "remote:"
    private const val AI_PLATFORM_PROTOCOL = "ai_platform:"

    fun parse(ref: String): DatasetRef {
      if (ref.startsWith(EXISTING_PROTOCOL)) {
        return ExistingRef(ref.substring(EXISTING_PROTOCOL.length))
      }

      if (ref.startsWith(REMOTE_PROTOCOL)) {
        return RemoteFileRef(ref.substring(REMOTE_PROTOCOL.length))
      }

      if (ref.startsWith(AI_PLATFORM_PROTOCOL)) {
        return AiPlatformFileRef(ref.substring(AI_PLATFORM_PROTOCOL.length))
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

internal data class RemoteFileRef(private val url: String) : DatasetRef {
  override val name: String = url
    .removePrefix("https://huggingface.co/datasets/JetBrains/eval_plugin/resolve/main/")
    .replace("/", "_")

  override fun prepare(datasetContext: DatasetContext) {
    val readToken = System.getenv("AIA_EVALUATION_DATASET_READ_TOKEN") ?: ""
    check(readToken.isNotBlank()) {
      "Token for dataset $url should be configured"
    }

    val content = httpGet(url, readToken)
    val path = datasetContext.path(name)
    path.toFile().writeText(content)
  }
}

internal data class AiPlatformFileRef(override val name: String): DatasetRef {
  override fun prepare(datasetContext: DatasetContext) { }
}
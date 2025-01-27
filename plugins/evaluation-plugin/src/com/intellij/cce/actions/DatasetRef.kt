package com.intellij.cce.actions

import com.intellij.cce.util.httpGet
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name


sealed interface DatasetRef {

  val name: String

  fun prepare(datasetContext: DatasetContext)

  fun resultPath(datasetContext: DatasetContext): Path = datasetContext.path(name)

  companion object {
    private const val CONFIG_PROTOCOL = "config:"
    private const val EXISTING_PROTOCOL = "existing:"
    private const val REMOTE_PROTOCOL = "remote:"
    private const val AI_PLATFORM_PROTOCOL = "ai_platform:"

    fun parse(ref: String): DatasetRef {
      if (ref.startsWith(EXISTING_PROTOCOL)) {
        return ExistingRef(ref.substring(EXISTING_PROTOCOL.length))
      }

      if (ref.startsWith(CONFIG_PROTOCOL)) {
        return ConfigRelativeRef(ref.substring(CONFIG_PROTOCOL.length))
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

      return AbsoluteRef(ref)
    }
  }
}

internal data class AbsoluteRef(val relativePath: String) : DatasetRef {
  override val name: String = Path.of(relativePath).name.toString()

  override fun prepare(datasetContext: DatasetContext) {
    val path = resultPath(datasetContext)
    check(path.exists()) {
      "Path ${relativePath} doesn't exist: ${path.absolutePathString()}"
    }
  }

  override fun resultPath(datasetContext: DatasetContext): Path = Path(relativePath)
}

internal data class ConfigRelativeRef(val relativePath: String) : DatasetRef {
  override val name: String = Path.of(relativePath).normalize().toString()

  override fun prepare(datasetContext: DatasetContext) {
    val path = resultPath(datasetContext)

    check(path.exists()) {
      "Config-relative path $relativePath does not exist: $path"
    }
  }

  override fun resultPath(datasetContext: DatasetContext): Path {
    val configPath = checkNotNull(datasetContext.configPath) {
      "Path $relativePath supposed to be relative to config, but there is no config explicitly provided. " +
      "Note that this option is only for test purposes and not supposed to be used in production."
    }

    return Path.of(configPath).parent.resolve(relativePath)
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
    val path = datasetContext.path(name)

    if (path.exists()) {
      return
    }

    val readToken = System.getenv("AIA_EVALUATION_DATASET_READ_TOKEN") ?: ""
    check(readToken.isNotBlank()) {
      "Token for dataset $url should be configured"
    }

    val content = httpGet(url, readToken)
    path.toFile().writeText(content)
  }
}

internal data class AiPlatformFileRef(override val name: String): DatasetRef {
  override fun prepare(datasetContext: DatasetContext) { }
}
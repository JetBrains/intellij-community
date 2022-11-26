package org.jetbrains.completion.full.line.local

import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

val modelsDir = Files.createTempDirectory("flcc-models").absolutePathString()

data class ModelDir(
  private val modelName: String,
  private val mavenName: String,
  private val modelsDirPath: String = modelsDir,
) {
  private val root = Paths.get(modelsDirPath, modelName).also {
    it.delete(true)
    it.createDirectories()
  }

  val model: File = File("$root/flcc.model")
  val config: File = File("$root/flcc.json")
  val tokenizer: File = File("$root/flcc.bpe")

  init {
    ModelsFiles.downloadModel(root, mavenName)
  }
}

object ModelsFiles {
  val gpt2_py_4L_512_83_q_local = ModelDir("gpt2-py-4L-512-83-q-local", "local-model-python/0.0.4/")

  //val gpt2_py_4L_256_78 = ModelDir("gpt2-py-4L-256-78", "local-model-python/0.0.7/")
  //val gpt2_py_6L_82_old_data = ModelDir("gpt2-py-6L-82-old-data", "local-model-python/0.0.6/")
  //val gpt2_py_4L_512_83_local = ModelDir("gpt2-py-4L-512-83-local")
  //val gpt2_py_6L_82_old_data_q_local = ModelDir("gpt2-py-6L-82-old-data-q-local")
  //val gpt2_py_18L_89_q_local = ModelDir("gpt2-py-18L-89-q-local")

  private fun url(modelPath: String, file: String) = URL(
    "https://packages.jetbrains.team/maven/p/ccrm/flcc-local-models" +
    "/org/jetbrains/completion/full/line/$modelPath/$file"
  )

  fun downloadModel(root: Path, modelPath: String) {
    downloadFile(root, modelPath, "flcc.model")
    downloadFile(root, modelPath, "flcc.json")
    downloadFile(root, modelPath, "flcc.bpe")
  }

  private fun downloadFile(root: Path, modelPath: String, fileName: String) {
    url(modelPath, fileName).openStream().use { Files.copy(it, root.resolve(fileName)) }
  }
}

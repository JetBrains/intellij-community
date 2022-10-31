package ml.intellij.nlc.local

import java.io.File

const val modelsDir = "/Users/Kirill.Krylov/flcc-models/local"

// TODO: load from s3
// Temporary convenience class
data class ModelDir(
    private val modelName: String,
    private val modelsDirPath: String = modelsDir,
    val model: File = File("$modelsDirPath/$modelName/flcc.model"),
    val config: File = File("$modelsDirPath/$modelName/flcc.json"),
    val tokenizer: File = File("$modelsDirPath/$modelName/flcc.bpe")
)

object ModelsFiles {
    val gpt2_py_4L_256_78 = ModelDir("gpt2-py-4L-256-78")
    val gpt2_py_4L_512_83_local = ModelDir("gpt2-py-4L-512-83-local")
    val gpt2_py_6L_82_old_data = ModelDir("gpt2-py-6L-82-old-data")
    val gpt2_py_6L_82_old_data_q_local = ModelDir("gpt2-py-6L-82-old-data-q-local")
    val gpt2_py_18L_89_q_local = ModelDir("gpt2-py-18L-89-q-local")
    val gpt2_py_4L_512_83_q_local = ModelDir("gpt2-py-4L-512-83-q-local")
}

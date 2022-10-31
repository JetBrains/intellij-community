package ml.intellij.nlc.local.generation.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.core.model.KIModel
import io.kinference.model.ExecutionContext
import io.kinference.ndarray.arrays.*
import ml.intellij.nlc.local.CompletionConfig
import ml.intellij.nlc.local.loader.CompletionModelLoader
import java.io.File

class GPT2ModelWrapper(
  var model: KIModel,
  var numAttentionHeads: Int,
  var hiddenSize: Int,
  var numLayer: Int,
  var vocabSize: Int,
  override val maxSeqLen: Int
) : ModelWrapper {
  data class Config(
    @SerializedName("n_head") val numAttentionHeads: Int,
    @SerializedName("n_embd") val hiddenSize: Int,
    @SerializedName("n_layer") val numLayer: Int,
    @SerializedName("vocab_size") val vocabSize: Int,
    @SerializedName("n_ctx") val maxSeqLen: Int
  )

  constructor(model: KIModel, config: Config) : this(
    model,
    config.numAttentionHeads,
    config.hiddenSize,
    config.numLayer,
    config.vocabSize,
    config.maxSeqLen
  )

  constructor(model: ByteArray, config: String) : this(KIEngine.loadModel(model), Gson().fromJson(config, Config::class.java))

  constructor(model_file: File, config_file: File) : this(model_file.readBytes(), config_file.readText())

  @Deprecated("CompletionConfig.Model will be deleted in future versions")
  constructor(loader: CompletionModelLoader, config: CompletionConfig.Model) : this(
    KIEngine.loadModel(loader.getModel()),
    Config(config.numAttentionHeads, config.hiddenSize, config.numLayer, config.vocabSize, config.maxSeqLen)
  )

  override fun initLogProbs(inputIds: Array<IntArray>, execContext: ExecutionContext): ModelOutputSeq {
    val batchSize = inputIds.size
    if (batchSize == 0) {
      return ModelOutputSeq()
    }

    val seqLen = inputIds[0].size
    val input = ArrayList<KITensor>()
    val longIds = LongNDArray(batchSize, seqLen) { (batch, pos): IntArray -> inputIds[batch][pos].toLong() }
    input.add(longIds.asTensor("input_ids"))
    input.add(FloatNDArray.ones(shape = intArrayOf(batchSize, seqLen)).asTensor("attention_mask"))
    input.add(LongNDArray(shape = intArrayOf(batchSize, seqLen)) { it.toLong() }.asTensor("position_ids"))

    val shape = intArrayOf(2, batchSize, numAttentionHeads, 0, hiddenSize / numAttentionHeads)
    for (i in 0 until numLayer) {
      val emptyPast = FloatNDArray.zeros(shape)
      input.add(emptyPast.asTensor("past_$i"))
    }

    return process(input, batchSize, seqLen, execContext)
  }

  override fun getLogProbs(
    inputIds: Array<IntArray>, past: List<NDArray>, execContext: ExecutionContext
  ): ModelOutputSeq {
    val batchSize = inputIds.size
    if (batchSize == 0) {
      return ModelOutputSeq()
    }

    val seqLen = inputIds[0].size
    val pastLength = past[0].shape[3]

    val input = ArrayList<KITensor>()
    val longIds = LongNDArray(batchSize, seqLen) { (batch, pos): IntArray -> inputIds[batch][pos].toLong() }
    input.add(longIds.asTensor("input_ids"))
    input.add(FloatNDArray.ones(shape = intArrayOf(batchSize, pastLength + seqLen)).asTensor("attention_mask"))
    input.add(
      LongNDArray(
        shape = intArrayOf(
          batchSize,
          seqLen
        )
      ) { (pastLength + it % seqLen).toLong() }.asTensor("position_ids")
    )

    past.forEachIndexed { i, state -> input.add((state as NDArrayCore).asTensor("past_$i")) }
    // (2, 1, 4, 4, 64)

    return process(input, batchSize, seqLen, execContext)
  }

  private fun process(
    input: ArrayList<KITensor>, batchSize: Int, seqLen: Int, execContext: ExecutionContext
  ): ModelOutputSeq {
    val output = model.predict(
      input = input,
      executionContext = execContext
    ).map { (it.value as KITensor).data }

    val ndProbs = output[0] as FloatNDArray
    val pointer = ndProbs.array.pointer()

    val probs = List(batchSize) { Array(seqLen) { DoubleArray(vocabSize) } }

    for ((batch, pos, id) in NDIndexer(ndProbs.shape)) {
      probs[batch][pos][id] = pointer.getAndIncrement().toDouble()
    }

    return ModelOutputSeq(probs, output.drop(1))
  }
}

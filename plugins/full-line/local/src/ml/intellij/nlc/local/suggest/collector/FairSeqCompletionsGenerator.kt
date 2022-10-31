package ml.intellij.nlc.local.suggest.collector

import io.kinference.model.ExecutionContext
import ml.intellij.nlc.local.CompletionConfig
import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.generation.generation.*
import ml.intellij.nlc.local.generation.generation.FairSeqGeneration
import ml.intellij.nlc.local.generation.model.ModelWrapper
import ml.intellij.nlc.local.tokenizer.Tokenizer

/**
 * Completion generator that is using FairSeq beam-search under the hood.
 */
internal class FairSeqCompletionsGenerator(model: ModelWrapper, tokenizer: Tokenizer) :
    BaseCompletionsGenerator<CompletionConfig.Generation>(model, tokenizer) {
    private val generation: Generation<CompletionConfig.Generation> = FairSeqGeneration(model, tokenizer)

    override fun generateWithSearch(
        context: String,
        prefix: String,
        config: CompletionConfig.Generation,
        execContext: ExecutionContext
    ): List<CompletionModel.CompletionResult> {
        val result = ArrayList<CompletionModel.CompletionResult>()
        val contextIds = makeContextIds(context, config, null)

        val completionsByLen = generation.generate(contextIds, prefix, config, execContext)
        for (completionsGroup in completionsByLen) {
            val completions = decodeSequences(completionsGroup)
            result.addAll(completions)
        }

        return result
    }

    private fun decodeSequences(sequences: List<GenerationInfo>): List<CompletionModel.CompletionResult> {
        return sequences.map { CompletionModel.CompletionResult(tokenizer.decode(it.ids), it) }
    }

    override fun trimCompletion(completion: CompletionModel.CompletionResult): CompletionModel.CompletionResult {
        return completion.trimEnding().trimAfterSentenceEnd()
    }

    private fun CompletionModel.CompletionResult.trimEnding(): CompletionModel.CompletionResult {
        if (this.text.isEmpty() || this.text[text.lastIndex].isLetterOrDigit()) {
            return this
        }
        var i = 1
        while (i <= this.text.length && !this.text[text.length - i].isLetterOrDigit()) i++
        i--
        val codedAll = tokenizer.encode(this.text)
        val codedTrimmed = tokenizer.encode(this.text.substring(0, this.text.length - i))

        var trimmedCompletion = this.text
        if (codedTrimmed.contentEquals(codedAll.copyOfRange(0, codedTrimmed.size))) {
            trimmedCompletion = trimmedCompletion.substring(0, trimmedCompletion.length - i)
            this.info.trim(codedTrimmed.size)
        }
        return CompletionModel.CompletionResult(trimmedCompletion, this.info)
    }

    private fun CompletionModel.CompletionResult.trimAfterSentenceEnd(): CompletionModel.CompletionResult {
        if (this.text.isEmpty()) {
            return this
        }

        var i = 0
        while (i < this.text.length && (this.text[i].isLetterOrDigit() || this.text[i] in " ,")) i++

        var trimmedCompletion = this.text
        if (i < this.text.length) {
            val codedAll = tokenizer.encode(this.text)
            val codedTrimmed = tokenizer.encode(this.text.substring(0, i))
            if (codedTrimmed.contentEquals(codedAll.copyOfRange(0, codedTrimmed.size))) {
                trimmedCompletion = this.text.substring(0, i)
                this.info.trim(codedTrimmed.size)
            }
        }

        return CompletionModel.CompletionResult(trimmedCompletion, this.info)
    }
}

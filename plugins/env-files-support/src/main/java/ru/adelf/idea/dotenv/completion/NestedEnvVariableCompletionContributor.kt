package ru.adelf.idea.dotenv.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.CommonProcessors.CollectUniquesProcessor
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import ru.adelf.idea.dotenv.DotEnvLanguage
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey
import ru.adelf.idea.dotenv.psi.DotEnvTypes
import ru.adelf.idea.dotenv.psi.DotEnvValue

class NestedEnvVariableCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC,
               psiElement(DotEnvTypes.KEY_CHARS)
                   .withLanguage(DotEnvLanguage.INSTANCE)
                   .withParent(DotEnvNestedVariableKey::class.java),
               NestedEnvVariableCompletionProvider()
        )
        extend(CompletionType.BASIC,
               psiElement(DotEnvTypes.VALUE_CHARS)
                   .withLanguage(DotEnvLanguage.INSTANCE)
                   .withParent(DotEnvValue::class.java),
               NestedVariableBlockCompletionProvider()
        )
    }

    private class NestedEnvVariableCompletionProvider : CompletionProvider<CompletionParameters?>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val position = parameters.position
            val index = FileBasedIndex.getInstance()
            val processor = CollectUniquesProcessor<String?>()
            index.processAllKeys<String?>(DotEnvKeyValuesIndex.KEY, processor, position.getProject())
            processor.getResults()
                .filterNotNull()
                .forEach { result.addElement(LookupElementBuilder.create(it)) }
        }

    }

    private class NestedVariableBlockCompletionProvider : CompletionProvider<CompletionParameters?>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            if (shouldComplete(parameters)) {
                val element = LookupElementBuilder
                    .create(INSERTED_TEXT)
                    .withPresentableText(PRESENTABLE_TEXT)
                    .withInsertHandler { context, element ->
                        val offset = context.startOffset + 2
                        context.editor.caretModel.moveToOffset(offset)
                    }
                result.withPrefixMatcher(MATCHED_PREFIX).addElement(element)
            }
        }

        fun shouldComplete(parameters: CompletionParameters): Boolean {
            return parameters.originalPosition?.prevSibling?.textMatches(DOUBLE_QUOTE_CHARACTER) == true
                   && (parameters.offset < 2 || parameters.editor.document.charsSequence[parameters.offset - 2] != ESCAPE_CHARACTER)
        }

        companion object {
            const val INSERTED_TEXT = "\${}"
            const val PRESENTABLE_TEXT = "\${...}"
            const val MATCHED_PREFIX = "$"
            const val ESCAPE_CHARACTER = '\\'
            const val DOUBLE_QUOTE_CHARACTER = "\""
        }

    }

}

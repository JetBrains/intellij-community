package org.jetbrains.completion.full.line.platform.ranking

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.completion.ml.CompletionMLPolicy
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

class FullLineCompletionPolicy : CompletionMLPolicy {
    override fun isReRankingDisabled(parameters: CompletionParameters): Boolean {
        val settings = MLServerCompletionSettings.getInstance()
        return settings.isEnabled() && settings.isEnabled(parameters.originalFile.language)
    }
}

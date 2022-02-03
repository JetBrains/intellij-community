package training.featuresSuggester.suggesters

import com.google.common.collect.EvictingQueue
import com.intellij.lang.Language
import com.intellij.openapi.ide.CopyPasteManager
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.BeforeEditorCopyAction
import training.featuresSuggester.actions.EditorCopyAction
import training.featuresSuggester.asString
import java.awt.datatransfer.Transferable
import java.util.*

class CopyPasteSuggester : AbstractFeatureSuggester() {
  override val id: String = "Paste from history"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("paste.from.history.name")

  override val message = FeatureSuggesterBundle.message("paste.from.history.message")
  override val suggestingActionId = "PasteMultiple"
  override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/working-with-source-code.html#copy_paste"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf(Language.ANY.id)

  private data class CopyData(val text: String, val timeMillis: Long)

  @Suppress("UnstableApiUsage")
  private val copyHistory: Queue<CopyData> = EvictingQueue.create(MAX_OCCURRENCE_INDEX + 1)

  override fun getSuggestion(action: Action): Suggestion {
    when (action) {
      is BeforeEditorCopyAction -> {
        val contents: Array<Transferable> = CopyPasteManager.getInstance().allContents
        val occurrenceIndex = contents.indexOfFirst { it.asString() == action.text }
        if (occurrenceIndex in MIN_OCCURRENCE_INDEX..MAX_OCCURRENCE_INDEX) {
          val prevAction = copyHistory.findLast { it.text == action.text } ?: return NoSuggestion
          val delta = action.timeMillis - prevAction.timeMillis
          if (delta < MAX_COPY_INTERVAL_TIME_MILLIS) {
            return createSuggestion()
          }
        }
      }
      is EditorCopyAction -> {
        copyHistory.add(CopyData(action.text, action.timeMillis))
      }
    }
    return NoSuggestion
  }

  companion object {
    const val MIN_OCCURRENCE_INDEX = 1
    const val MAX_OCCURRENCE_INDEX = 2
    const val MAX_COPY_INTERVAL_TIME_MILLIS = 20000L
  }
}

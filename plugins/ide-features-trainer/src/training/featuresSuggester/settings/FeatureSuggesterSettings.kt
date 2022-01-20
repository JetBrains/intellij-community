package training.featuresSuggester.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import training.featuresSuggester.suggesters.FeatureSuggester
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@State(
  name = "FeatureSuggesterSettings",
  storages = [Storage("FeatureSuggester.xml")]
)
class FeatureSuggesterSettings : PersistentStateComponent<FeatureSuggesterSettings> {
  var suggesters: MutableMap<String, Boolean> = FeatureSuggester.suggesters.associate { it.id to true }.toMutableMap()

  // List of timestamps (millis) of the first IDE session start for the last days
  var workingDays: MutableList<Long> = mutableListOf()

  override fun getState(): FeatureSuggesterSettings {
    return this
  }

  override fun loadState(state: FeatureSuggesterSettings) {
    suggesters = state.suggesters
    workingDays = state.workingDays
  }

  fun isEnabled(suggesterId: String): Boolean {
    return suggesters[suggesterId] == true
  }

  fun setEnabled(suggesterId: String, enabled: Boolean) {
    suggesters[suggesterId] = enabled
  }

  fun updateWorkingDays() {
    val curTime = System.currentTimeMillis()
    val lastTime = workingDays.lastOrNull()
    if (lastTime == null) {
      workingDays.add(curTime)
    }
    else if (curTime.toLocalDate() != lastTime.toLocalDate()) {
      val numToRemove = workingDays.size - maxSuggestingIntervalDays + 1
      if (numToRemove > 0) {
        workingDays.subList(0, numToRemove).clear()
      }
      workingDays.add(curTime)
    }
  }

  /**
   * Return the start time of session happened [oldestDayNum] working days (when user performed any action) ago.
   * If there is no information about so old sessions it will return at least the current time minus [oldestDayNum] days
   * (it is required for migration of existing users).
   * So returned time always will be earlier then time [oldestDayNum] days ago.
   */
  fun getOldestWorkingDayStartMillis(oldestDayNum: Int): Long {
    val simpleOldestTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(oldestDayNum.toLong())
    return if (workingDays.isNotEmpty()) {
      val ind = max(0, workingDays.size - oldestDayNum)
      min(workingDays[ind], simpleOldestTime)
    }
    else simpleOldestTime
  }

  private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

  companion object {
    @JvmStatic
    fun instance(): FeatureSuggesterSettings {
      return ApplicationManager.getApplication().getService(FeatureSuggesterSettings::class.java)
    }

    private val maxSuggestingIntervalDays: Int by lazy {
      FeatureSuggester.suggesters.maxOfOrNull { it.minSuggestingIntervalDays } ?: run {
        thisLogger().error("Failed to find registered suggesters")
        14
      }
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import training.learn.course.Lesson
import training.util.trainerPluginConfigName

@State(name = "LessonStateBase", storages = [Storage(value = trainerPluginConfigName)], category = SettingsCategory.TOOLS)
private class LessonStateBase : PersistentStateComponent<LessonStateBase> {

  override fun getState(): LessonStateBase = this

  override fun loadState(persistedState: LessonStateBase) {
    map = persistedState.map.mapKeys { it.key.lowercase() }.toMutableMap()
    migrateLessonState("java.onboarding", "idea.onboarding")
  }

  var map: MutableMap<String, LessonState> = mutableMapOf()

  @Suppress("SameParameterValue")
  private fun migrateLessonState(oldId: String, newId: String) {
    map[oldId]?.let {
      map[newId] = it
      map.remove(oldId)
    }
  }

  companion object {
    internal val instance: LessonStateBase
      get() = ApplicationManager.getApplication().getService(LessonStateBase::class.java)
  }
}

internal object LessonStateManager {

  fun setPassed(lesson: Lesson) {
    LessonStateBase.instance.map[lesson.id.lowercase()] = LessonState.PASSED
  }

  fun resetPassedStatus() {
    for (lesson in LessonStateBase.instance.map) {
      lesson.setValue(LessonState.NOT_PASSED)
    }
  }

  fun getPassedLessonsNumber(): Int = LessonStateBase.instance.map.values.filter { it == LessonState.PASSED }.size

  fun getStateFromBase(lessonId: String): LessonState =
    LessonStateBase.instance.map.getOrPut(lessonId.lowercase()) { LessonState.NOT_PASSED }
}

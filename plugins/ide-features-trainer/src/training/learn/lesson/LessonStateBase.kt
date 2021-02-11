// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import training.learn.course.Lesson
import training.util.trainerPluginConfigName

@State(name = "LessonStateBase", storages = [Storage(value = trainerPluginConfigName)])
class LessonStateBase : PersistentStateComponent<LessonStateBase> {

  override fun getState(): LessonStateBase = this

  override fun loadState(persistedState: LessonStateBase) {
    map = persistedState.map.mapKeys { it.key.toLowerCase() }.toMutableMap()
  }

  var map: MutableMap<String, LessonState> = mutableMapOf()

  companion object {
    internal val instance: LessonStateBase
    get() = ApplicationManager.getApplication().getService(LessonStateBase::class.java)
  }
}

object LessonStateManager {

  fun setPassed(lesson: Lesson) {
    LessonStateBase.instance.map[lesson.id.toLowerCase()] = LessonState.PASSED
  }

  fun resetPassedStatus() {
    for (lesson in LessonStateBase.instance.map) {
      lesson.setValue(LessonState.NOT_PASSED)
    }
  }

  fun getStateFromBase(lessonId: String): LessonState = LessonStateBase.instance.map.getOrPut(lessonId.toLowerCase(), { LessonState.NOT_PASSED })
}

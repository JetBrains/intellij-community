// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.io.json
import training.dsl.TaskTestContext
import training.learn.CourseManager
import training.learn.course.KLesson
import training.learn.course.Lesson
import training.learn.lesson.LessonListener
import training.statistic.LessonStartingWay
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val LOG: Logger = Logger.getInstance(LearningLessonsAutoExecutor::class.java)

@Suppress("HardCodedStringLiteral")
class LearningLessonsAutoExecutor(val project: Project, private val progress: ProgressIndicator) {
  private fun runSingleLesson(lesson: Lesson) {
    invokeAndWaitIfNeeded {
      // starting way does not matter because it should not be executed on release builds
      CourseManager.instance.openLesson(project, lesson, LessonStartingWay.LEARN_TAB)
    }
    try {
      executeLesson(lesson)
    }
    catch (e: TimeoutException) {
      // Check lesson state later
    }
    if (!lesson.passed) {
      LOG.error("Can't pass lesson " + lesson.name)
    }
  }

  private fun runAllLessons(): Map<String, Long> {
    val durations = mutableMapOf<String, Long>()
    TaskTestContext.inTestMode = true
    val lessons = CourseManager.instance.lessonsForModules

    for (lesson in lessons) {
      if (lesson !is KLesson || lesson.testScriptProperties.skipTesting) continue
      if (durations.containsKey(lesson.id)) continue // Just duplicate from another module
      progress.checkCanceled()
      // Some lessons may have post-completed activities (in onLessonEnd)
      Thread.sleep(1000)
      val duration = TimeoutUtil.measureExecutionTime<Throwable> {
        runSingleLesson(lesson)
      }
      durations[lesson.id] = duration
    }
    TaskTestContext.inTestMode = false
    return durations
  }

  private fun executeLesson(lesson: Lesson) {
    val lessonPromise = AsyncPromise<Boolean>()
    lesson.addLessonListener(object : LessonListener {
      override fun lessonPassed(lesson: Lesson) {
        lessonPromise.setResult(true)
      }
    })
    progress.checkCanceled()
    val passedStatus = lessonPromise.blockingGet(lesson.testScriptProperties.duration, TimeUnit.SECONDS)
    if (passedStatus == null || !passedStatus) {
      LOG.error("Can't pass lesson " + lesson.name)
    }
    else {
      System.err.println("Passed " + lesson.name)
    }
  }

  companion object {
    fun runAllLessons(project: Project) {
      runBackgroundableTask("Running All Lessons", project) {
        try {
          val learningLessonsAutoExecutor = LearningLessonsAutoExecutor(project, it)
          val durations = learningLessonsAutoExecutor.runAllLessons()
          System.setProperty("ift.gui.result", getJsonStatus(durations))
        }
        finally {
          TaskTestContext.inTestMode = false
        }
      }
    }

    fun runSingleLesson(project: Project, lesson: Lesson) {
      runBackgroundableTask("Running lesson ${lesson.name}", project) {
        TaskTestContext.inTestMode = true
        try {
          val learningLessonsAutoExecutor = LearningLessonsAutoExecutor(project, it)
          learningLessonsAutoExecutor.runSingleLesson(lesson)
        }
        finally {
          TaskTestContext.inTestMode = false
        }
      }
    }

    private fun getJsonStatus(durations: Map<String, Long>): String {
      val result = StringBuilder()

      result.json {
        array("lessons") {
          for (lesson in CourseManager.instance.lessonsForModules) {
            if (lesson !is KLesson || lesson.testScriptProperties.skipTesting) continue
            result.json {
              "id" to lesson.id
              "passed" to lesson.passed
              "duration" toRaw durations[lesson.id].toString()
            }
          }
        }
      }
      return result.toString()
    }
  }
}
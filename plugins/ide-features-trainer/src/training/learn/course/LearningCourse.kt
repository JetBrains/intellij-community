// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import org.jetbrains.annotations.ApiStatus

interface LearningCourse {
  fun modules(): Collection<IftModule>

  @ApiStatus.Internal
  fun otherCoursesMergeStrategy(): LearningCoursesMergeStrategy = LearningCoursesMergeStrategy.ADD_MODULES

  /**
   * @return map of lesson id to the list of suitable [com.intellij.ide.util.TipAndTrickBean.getId]
   */
  fun getLessonIdToTipsMap(): Map<String, List<String>> = emptyMap()
}

@ApiStatus.Internal
enum class LearningCoursesMergeStrategy {
  ADD_MODULES,
  REPLACE_MODULES
}
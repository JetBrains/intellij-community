// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

interface LearningCourse {
  fun modules(): Collection<IftModule>

  /**
   * @return map of lesson id to the list of suitable [com.intellij.ide.util.TipAndTrickBean.getId]
   */
  fun getLessonIdToTipsMap(): Map<String, List<String>> = emptyMap()
}
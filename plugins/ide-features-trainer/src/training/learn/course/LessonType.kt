// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

enum class LessonType(internal val isProject: Boolean,
                      internal val isSingleEditor: Boolean,
) {
  SCRATCH(false, true),
  SINGLE_EDITOR(true, true),
  PROJECT(true, false),
  USER_PROJECT(true, false),
}
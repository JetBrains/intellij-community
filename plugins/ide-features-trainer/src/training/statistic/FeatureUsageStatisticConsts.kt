// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

object FeatureUsageStatisticConsts {
  const val LESSON_ID = "lesson_id"
  const val LANGUAGE = "language"
  const val DURATION = "duration"
  const val START = "start"
  const val PASSED = "passed"
  const val GROUP_EVENT = "group_event"
  const val GROUP_NAME = "group_name"
  const val GROUP_STATE = "group_state"
  const val GROUP_EXPANDED = "expanded"
  const val GROUP_COLLAPSED = "collapsed"
  const val START_MODULE_ACTION = "start_module_action"
  const val MODULE_NAME = "module_name"
  const val PROGRESS_PERCENTAGE = "progress_percentage"
}

enum class GroupNames {
  TUTORIALS,
  PROJECTS
}
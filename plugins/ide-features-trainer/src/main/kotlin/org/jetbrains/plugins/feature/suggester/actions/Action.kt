package org.jetbrains.plugins.feature.suggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.project.Project

abstract class Action {
    abstract val timeMillis: Long
    abstract val language: Language?
    abstract val project: Project?
}

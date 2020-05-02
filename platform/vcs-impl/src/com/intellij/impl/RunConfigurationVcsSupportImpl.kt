// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl

import com.intellij.execution.configurations.RunConfigurationVcsSupport
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager

class RunConfigurationVcsSupportImpl : RunConfigurationVcsSupport() {
    override fun hasActiveVcss(project: Project): Boolean {
        return ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
    }
    override fun isDirectoryVcsIgnored(project: Project, path: String): Boolean {
        return VcsIgnoreManager.getInstance(project).isDirectoryVcsIgnored(path)
    }
}
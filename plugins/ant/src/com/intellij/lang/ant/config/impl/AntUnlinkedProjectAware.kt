// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl

import com.intellij.lang.ant.config.AntBuildFile
import com.intellij.lang.ant.config.AntConfigurationBase
import com.intellij.lang.ant.config.AntConfigurationListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil

class AntUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {
  override val systemId = SYSTEM_ID

  override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean {
    return buildFile.name in KNOWN_ANT_FILES
  }

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    val antConfiguration = AntConfigurationBase.getInstance(project)
    return antConfiguration.buildFiles.asSequence()
      .mapNotNull { it.virtualFile?.path }
      .any { FileUtil.pathsEqual(PathUtil.getParentPath(it), externalProjectPath) }
  }

  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    val localFileSystem = LocalFileSystem.getInstance()
    val externalProjectDir = localFileSystem.findFileByPath(externalProjectPath)
    if (externalProjectDir == null) {
      val shortPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(externalProjectPath), false)
      throw IllegalArgumentException(ExternalSystemBundle.message("error.project.does.not.exist", systemId.readableName, shortPath))
    }
    val antConfiguration = AntConfigurationBase.getInstance(project)
    externalProjectDir.children.asSequence()
      .filter { isBuildFile(project, it) }
      .forEach { antConfiguration.addBuildFile(it) }
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    val antConfiguration = AntConfigurationBase.getInstance(project)
    antConfiguration.addAntConfigurationListener(object : AntConfigurationListener {
      override fun buildFileAdded(buildFile: AntBuildFile) {
        val virtualFile = buildFile.virtualFile ?: return
        listener.onProjectLinked(PathUtil.getParentPath(virtualFile.path))
      }

      override fun buildFileRemoved(buildFile: AntBuildFile) {
        val virtualFile = buildFile.virtualFile ?: return
        listener.onProjectUnlinked(PathUtil.getParentPath(virtualFile.path))
      }
    })
  }
}
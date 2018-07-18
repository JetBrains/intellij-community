/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.projectRoots

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.OutOfSourcesChecker
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.groovy.GroovyFileType

class GroovyOutOfSourcesChecker : OutOfSourcesChecker {

  override fun getFileType(): GroovyFileType = GroovyFileType.GROOVY_FILE_TYPE

  override fun isOutOfSources(project: Project, virtualFile: VirtualFile): Boolean {
    return !ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(virtualFile, ROOT_TYPES)
  }
}
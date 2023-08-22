/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.builtInWebServer.FileResolver
import org.jetbrains.builtInWebServer.PathInfo
import org.jetbrains.builtInWebServer.PathQuery
import org.jetbrains.builtInWebServer.PrefixlessWebServerRootsProvider

internal class VcsRootWebServerRootsProvider : PrefixlessWebServerRootsProvider() {
  override fun resolve(path: String, project: Project, resolver: FileResolver, pathQuery: PathQuery): PathInfo? {
    for (vcsRoot in ProjectLevelVcsManager.getInstance(project).allVcsRoots) {
      val root = if (vcsRoot.vcs != null) vcsRoot.path else continue
      val virtualFile = resolver.resolve(path, root, pathQuery = pathQuery)
      if (virtualFile != null) return virtualFile
    }
    return null
  }

  override fun getPathInfo(file: VirtualFile, project: Project): PathInfo? {
    for (vcsRoot in ProjectLevelVcsManager.getInstance(project).allVcsRoots) {
      val root = if (vcsRoot.vcs != null) vcsRoot.path else continue
      if (VfsUtilCore.isAncestor(root, file, true)) {
        return PathInfo(null, file, root)
      }
    }
    return null
  }
}
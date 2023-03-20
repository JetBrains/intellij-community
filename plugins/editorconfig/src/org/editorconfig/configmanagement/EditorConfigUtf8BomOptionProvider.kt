// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement

import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.Utf8BomOptionProvider
import org.editorconfig.Utils

internal class EditorConfigUtf8BomOptionProvider : Utf8BomOptionProvider {
  override fun shouldAddBOMForNewUtf8File(file: VirtualFile): Boolean {
    if (!Utils.isApplicableTo(file)) return false
    val project = ProjectLocator.getInstance().guessProjectForFile(file)
    return EditorConfigEncodingCache.getInstance().getUseUtf8Bom(project, file)
  }
}
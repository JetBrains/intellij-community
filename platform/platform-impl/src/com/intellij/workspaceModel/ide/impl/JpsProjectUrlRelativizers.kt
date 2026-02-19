// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectUrlRelativizer
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun createJpsProjectUrlRelativizer(project: Project): UrlRelativizer = JpsProjectUrlRelativizer(project.basePath, insideIdeProcess = true)
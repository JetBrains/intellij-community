// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory

internal class IdeK1VirtualFileFinderFactory(project: Project): IdeVirtualFileFinderFactory(project), MetadataFinderFactory
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project

fun <T : Any> addExtensionPointInTest(
    pointName: ProjectExtensionPointName<T>,
    project: Project,
    provider: T,
    testRootDisposable: Disposable
) {
    pointName.getPoint(project).registerExtension(provider, testRootDisposable)
}

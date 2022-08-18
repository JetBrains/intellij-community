// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.scripting

import com.intellij.openapi.application.Application

// this property is used in tests to avoid full gradle project import and to make those tests more lightweight
var testAffectedGradleProjectFiles: Boolean = false

internal val Application.isUnitTestModeWithoutAffectedGradleProjectFilesCheck: Boolean
    get() {
        if (isUnitTestMode) {
            return !testAffectedGradleProjectFiles
        }
        return false
    }
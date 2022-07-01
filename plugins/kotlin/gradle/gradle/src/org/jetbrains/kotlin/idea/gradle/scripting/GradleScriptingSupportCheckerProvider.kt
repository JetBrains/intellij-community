// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.scripting

import org.jetbrains.kotlin.idea.script.configuration.SupportedScriptScriptingSupportCheckerProvider

class GradleScriptingSupportCheckerProvider: SupportedScriptScriptingSupportCheckerProvider(".gradle.kts", true)
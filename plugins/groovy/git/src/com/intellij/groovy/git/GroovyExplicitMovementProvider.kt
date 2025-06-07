// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.git

import git4idea.checkin.ConvertLanguageExplicitMovementProvider
import org.jetbrains.plugins.groovy.refactoring.convertToJava.git.pathBeforeGroovyToJavaConversion

private class GroovyExplicitMovementProvider : ConvertLanguageExplicitMovementProvider("groovy", "java", pathBeforeGroovyToJavaConversion)

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsClassNameAware
import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument
import java.io.Serializable

class ExtractedCompilerArgumentsBucket(
    override val compilerArgumentsClassName: String,
    val singleArguments: Map<String, String?> = emptyMap(),
    val classpathParts: Array<String> = emptyArray(),
    val multipleArguments: Map<String, Array<String>?> = emptyMap(),
    val flagArguments: Map<String, Boolean> = emptyMap(),
    val internalArguments: List<String> = emptyList(),
    val freeArgs: List<String> = emptyList()
) : CompilerArgumentsClassNameAware<String>, Serializable

class CachedCompilerArgumentsBucket(
    override val compilerArgumentsClassName: KotlinCachedRegularCompilerArgument,
    val singleArguments: Map<KotlinCachedCompilerArgument<*>, KotlinCachedCompilerArgument<*>>,
    val classpathParts: KotlinCachedMultipleCompilerArgument,
    val multipleArguments: Map<KotlinCachedCompilerArgument<*>, KotlinCachedCompilerArgument<*>>,
    val flagArguments: Map<KotlinCachedCompilerArgument<*>, KotlinCachedCompilerArgument<*>>,
    val internalArguments: List<KotlinCachedCompilerArgument<*>>,
    val freeArgs: List<KotlinCachedCompilerArgument<*>>
) : CompilerArgumentsClassNameAware<KotlinCachedRegularCompilerArgument>, Serializable

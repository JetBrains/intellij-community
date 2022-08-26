// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Named
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull

fun KotlinCompilationReflection(kotlinCompilation: Any): KotlinCompilationReflection =
    KotlinCompilationReflectionImpl(kotlinCompilation)

interface KotlinCompilationReflection {
    val target: KotlinTargetReflection?
    val compilationName: String
    val gradleCompilation: Named
    val sourceSets: Collection<Named>? // Source Sets that directly added to compilation
    val allSourceSets: Collection<Named>? // this.sourceSets + their transitive closure through dependsOn relation
    val compilationOutput: KotlinCompilationOutputReflection?
    val konanTargetName: String?
    val compileKotlinTaskName: String?
    val associateCompilations: Iterable<KotlinCompilationReflection>
}

private class KotlinCompilationReflectionImpl(private val instance: Any) : KotlinCompilationReflection {

    override val target: KotlinTargetReflection? by lazy {
        KotlinTargetReflection(instance.callReflectiveAnyGetter("getTarget", logger) ?: return@lazy null)
    }

    override val gradleCompilation: Named
        get() = instance as Named

    override val compilationName: String by lazy {
        gradleCompilation.name
    }
    override val sourceSets: Collection<Named>? by lazy {
        instance.callReflectiveGetter("getKotlinSourceSets", logger)
    }

    override val allSourceSets: Collection<Named>? by lazy {
        instance.callReflectiveGetter("getAllKotlinSourceSets", logger)
    }

    override val compilationOutput: KotlinCompilationOutputReflection? by lazy {
        instance.callReflectiveAnyGetter("getOutput", logger)?.let { gradleOutput -> KotlinCompilationOutputReflection(gradleOutput) }
    }

    // Get konanTarget (for native compilations only).
    override val konanTargetName: String? by lazy {
        if (instance.javaClass.getMethodOrNull("getKonanTarget") == null) null
        else instance.callReflectiveAnyGetter("getKonanTarget", logger)
            ?.callReflectiveGetter("getName", logger)
    }
    override val compileKotlinTaskName: String? by lazy {
        instance.callReflectiveGetter("getCompileKotlinTaskName", logger)
    }

    override val associateCompilations: Iterable<KotlinCompilationReflection> by lazy {
        instance.callReflectiveGetter<List<*>>("getAssociateWith", logger).orEmpty()
            .filterNotNull()
            .map { compilation -> KotlinCompilationReflection(compilation) }
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinCompilationReflection::class.java)
    }
}

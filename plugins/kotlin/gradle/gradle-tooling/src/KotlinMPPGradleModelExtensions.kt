package org.jetbrains.kotlin.gradle

@ExperimentalGradleToolingApi
fun KotlinMPPGradleModel.getCompilations(sourceSet: KotlinSourceSet): Set<KotlinCompilation> {
    return targets.flatMap { target -> target.compilations }
        .filter { compilation -> compilationDependsOnSourceSet(compilation, sourceSet) }
        .toSet()
}

@ExperimentalGradleToolingApi
fun KotlinMPPGradleModel.compilationDependsOnSourceSet(
    compilation: KotlinCompilation, sourceSet: KotlinSourceSet
): Boolean {
    return compilation.declaredSourceSets.any { sourceSetInCompilation ->
        sourceSetInCompilation == sourceSet || sourceSetInCompilation.isDependsOn(this, sourceSet)
    }
}

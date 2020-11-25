package org.jetbrains.kotlin.gradle

@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "APIs can change with every release")
annotation class ExperimentalKotlinMPPGradleModelExtensionsApi

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinMPPGradleModel.getCompilations(sourceSet: KotlinSourceSet): Set<KotlinCompilation> {
    return targets.flatMap { target -> target.compilations }
        .filter { compilation -> compilationDependsOnSourceSet(compilation, sourceSet) }
        .toSet()
}

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinMPPGradleModel.compilationDependsOnSourceSet(
    compilation: KotlinCompilation, sourceSet: KotlinSourceSet
): Boolean {
    return compilation.sourceSets.any { sourceSetInCompilation ->
        sourceSetInCompilation == sourceSet || sourceSetInCompilation.isDependsOn(this, sourceSet)
    }
}

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinMPPGradleModel.resolveDeclaredDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return sourceSet.dependsOnSourceSets.mapNotNull { name -> sourceSets[name] }.toSet()
}

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinMPPGradleModel.resolveAllDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return mutableSetOf<KotlinSourceSet>().apply {
        val declaredDependencySourceSets = resolveDeclaredDependsOnSourceSets(sourceSet)
        addAll(declaredDependencySourceSets)
        for (declaredDependencySourceSet in declaredDependencySourceSets) {
            addAll(resolveAllDependsOnSourceSets(declaredDependencySourceSet))
        }
    }
}

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinMPPGradleModel.isDependsOn(from: KotlinSourceSet, to: KotlinSourceSet): Boolean {
    return to in resolveAllDependsOnSourceSets(from)
}

@ExperimentalKotlinMPPGradleModelExtensionsApi
fun KotlinSourceSet.isDependsOn(model: KotlinMPPGradleModel, sourceSet: KotlinSourceSet): Boolean {
    return model.isDependsOn(from = this, to = sourceSet)
}

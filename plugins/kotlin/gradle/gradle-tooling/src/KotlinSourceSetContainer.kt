package org.jetbrains.kotlin.gradle

interface KotlinSourceSetContainer {
    val sourceSetsByName: Map<String, KotlinSourceSet>
}

val KotlinSourceSetContainer.sourceSets: List<KotlinSourceSet> get() = sourceSetsByName.values.toList()

fun KotlinSourceSetContainer.resolveDeclaredDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return sourceSet.declaredDependsOnSourceSets.mapNotNull { name -> sourceSetsByName[name] }.toSet()
}

fun KotlinSourceSetContainer.resolveAllDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return mutableSetOf<KotlinSourceSet>().apply {
        val declaredDependencySourceSets = resolveDeclaredDependsOnSourceSets(sourceSet)
        addAll(declaredDependencySourceSets)
        for (declaredDependencySourceSet in declaredDependencySourceSets) {
            addAll(resolveAllDependsOnSourceSets(declaredDependencySourceSet))
        }
    }
}

fun KotlinSourceSetContainer.isDependsOn(from: KotlinSourceSet, to: KotlinSourceSet): Boolean {
    return to in resolveAllDependsOnSourceSets(from)
}

fun KotlinSourceSet.isDependsOn(model: KotlinSourceSetContainer, sourceSet: KotlinSourceSet): Boolean {
    return model.isDependsOn(from = this, to = sourceSet)
}

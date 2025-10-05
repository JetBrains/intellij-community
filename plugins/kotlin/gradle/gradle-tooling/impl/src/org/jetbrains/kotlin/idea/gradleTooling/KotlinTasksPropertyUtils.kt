// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.ConventionJavaPluginAccessor
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.FactoryNamedDomainObjectContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder.Companion.getSourceSetName
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder.Companion.kotlinPluginWrapper
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder.Companion.kotlinProjectExtensionClass
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder.Companion.kotlinSourceSetClass
import org.jetbrains.kotlin.idea.projectModel.KotlinTaskProperties
import java.io.File

data class KotlinTaskPropertiesImpl(
    override val incremental: Boolean?,
    override val packagePrefix: String?,
    override val pureKotlinSourceFolders: List<File>?,
    override val pluginVersion: String?
) : KotlinTaskProperties {
    constructor(kotlinTaskProperties: KotlinTaskProperties) : this(
        kotlinTaskProperties.incremental,
        kotlinTaskProperties.packagePrefix,
        kotlinTaskProperties.pureKotlinSourceFolders?.map { it }?.toList(),
        kotlinTaskProperties.pluginVersion
    )
}

typealias KotlinTaskPropertiesBySourceSet = MutableMap<String, KotlinTaskProperties>

private fun Task.getPackagePrefix(): String? {
    try {
        val getJavaPackagePrefix = this.javaClass.getMethod("getJavaPackagePrefix")
      return (getJavaPackagePrefix.invoke(this) as? String)
    } catch (e: Exception) {
    }
    return null
}

private fun Task.getIsIncremental(): Boolean? {
    try {
        val abstractKotlinCompileClass = javaClass.classLoader.loadClass(AbstractKotlinGradleModelBuilder.ABSTRACT_KOTLIN_COMPILE_CLASS)
        val getIncremental = abstractKotlinCompileClass.getDeclaredMethod("getIncremental")
      return (getIncremental.invoke(this) as? Boolean)
    } catch (e: Exception) {
    }
    return null
}

private fun Task.getPureKotlinSourceRoots(sourceSet: String, disambiguationClassifier: String? = null): List<File>? {
    try {
        val kotlinExtensionClass = project.extensions.findByType(javaClass.classLoader.loadClass(kotlinProjectExtensionClass))
        val getKotlinMethod = javaClass.classLoader.loadClass(kotlinSourceSetClass).getMethod("getKotlin")
        val classifier = if (disambiguationClassifier == "metadata") "common" else disambiguationClassifier
        val kotlinSourceSet = (kotlinExtensionClass?.javaClass?.getMethod("getSourceSets")?.invoke(kotlinExtensionClass)
                as? FactoryNamedDomainObjectContainer<Any>)?.asMap?.get(compilationFullName(sourceSet, classifier)) ?: return null
        val pureJava: Set<File>? = getJavaSourceRoot(project, sourceSet)
        return (getKotlinMethod.invoke(kotlinSourceSet) as? SourceDirectorySet)?.srcDirs?.filter {
            !(pureJava?.contains(it) ?: false)
        }?.toList()
    } catch (e: Exception) {
    }
    return null
}

private fun getJavaSourceRoot(project: Project, sourceSet: String): Set<File>? {
    val javaSourceSet: SourceSet? = if (GradleVersionUtil.isGradleAtLeast(project.gradle.gradleVersion, "8.2")) {
        project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.asMap[sourceSet] as SourceSet
    } else {
        ConventionJavaPluginAccessor(project).sourceSetContainer?.asMap[sourceSet]
    }
    return javaSourceSet?.java?.srcDirs
}

private fun Task.getKotlinPluginVersion(): String? {
    try {
        val pluginWrapperClass = javaClass.classLoader.loadClass(kotlinPluginWrapper)
        val getVersionMethod =
            pluginWrapperClass.getMethod("getKotlinPluginVersion", javaClass.classLoader.loadClass("org.gradle.api.Project"))
        return getVersionMethod.invoke(null, this.project) as String
    } catch (e: Exception) {
    }
    return null
}

fun KotlinTaskPropertiesBySourceSet.acknowledgeTask(compileTask: Task, classifier: String?) {
    this[compileTask.getSourceSetName()] =
        getKotlinTaskProperties(compileTask, classifier)
}

fun getKotlinTaskProperties(compileTask: Task, classifier: String?): KotlinTaskPropertiesImpl {
    return KotlinTaskPropertiesImpl(
        compileTask.getIsIncremental(),
        compileTask.getPackagePrefix(),
        compileTask.getPureKotlinSourceRoots(compileTask.getSourceSetName(), classifier),
        compileTask.getKotlinPluginVersion()
    )
}

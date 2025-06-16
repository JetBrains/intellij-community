import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

operator fun KotlinSourceSet.invoke(builder: SourceSetHierarchyBuilder.() -> Unit): KotlinSourceSet {
    SourceSetHierarchyBuilder(this).builder()
    return this
}

class SourceSetHierarchyBuilder(private val node: KotlinSourceSet) {
    operator fun KotlinSourceSet.unaryMinus() = this.dependsOn(node)
}

plugins {
    kotlin("multiplatform")
}

fun registerListDependenciesTask(sourceSet: KotlinSourceSet) {
    tasks.register("list${sourceSet.name.capitalize()}Dependencies") {
        doLast {
            val dependencies = project.configurations.findByName(
                "${sourceSet.name}IntransitiveDependenciesMetadata"
            )?.files.orEmpty()

            logger.quiet("${sourceSet.name} Dependencies | Count: ${dependencies.size}")

            dependencies.forEach { dependencyFile ->
                logger.quiet("Dependency: ${dependencyFile.path}")
            }
        }
    }
}

kotlin {

    when {
        HostManager.hostIsMac -> macosX64("nativePlatform")
        HostManager.hostIsLinux -> linuxX64("nativePlatform")
        HostManager.hostIsMingw -> mingwX64("nativePlatform")
        else -> throw IllegalStateException("Unsupported host")
    }

    when {
        HostManager.hostIsMac -> mingwX64("unsupportedNativePlatform")
        else -> macosX64("unsupportedNativePlatform")
    }

    jvm()

    val commonMain by sourceSets.getting
    val jvmMain by sourceSets.getting
    val nativeMain by sourceSets.creating
    val nativeMainParent by sourceSets.creating
    val nativePlatformMain by sourceSets.getting
    val unsupportedNativePlatformMain by sourceSets.getting

    commonMain {
        -jvmMain
        -nativeMainParent {
            -nativeMain {
                -nativePlatformMain
                -unsupportedNativePlatformMain
            }
        }
    }

    registerListDependenciesTask(commonMain)
    registerListDependenciesTask(nativeMain)
    registerListDependenciesTask(nativeMainParent)

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }
}




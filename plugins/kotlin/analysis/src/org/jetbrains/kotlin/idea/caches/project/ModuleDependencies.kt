package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.idea.project.isHMPPEnabled
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.isNative

internal fun Module.getIdeaModelDependencies(
    forProduction: Boolean,
    platform: TargetPlatform
): List<IdeaModuleInfo> {
    // Use StringBuilder so that all lines are written into the log atomically (otherwise
    // logs of call to getIdeaModelDependencies for several different modules interleave, leading
    // to unreadable mess)
    val debugString: StringBuilder? = if (LOG.isDebugEnabled) StringBuilder() else null
    debugString?.appendLine("Building idea model dependencies for module ${this}, platform=${platform}, forProduction=$forProduction")

    val allIdeaModuleInfoDependencies = resolveDependenciesFromOrderEntries(debugString, forProduction)
    val supportedModuleInfoDependencies = selectSupportedDependencies(debugString, platform, allIdeaModuleInfoDependencies)

    LOG.debug(debugString?.toString())

    return supportedModuleInfoDependencies.toList()
}

private fun Module.resolveDependenciesFromOrderEntries(
    debugString: StringBuilder?,
    forProduction: Boolean,
): Set<IdeaModuleInfo> {

    //NOTE: lib dependencies can be processed several times during recursive traversal
    val result = LinkedHashSet<IdeaModuleInfo>()
    val dependencyEnumerator = ModuleRootManager.getInstance(this).orderEntries().compileOnly().recursively().exportedOnly()
    if (forProduction && getBuildSystemType() == BuildSystemType.JPS) {
        dependencyEnumerator.productionOnly()
    }

    debugString?.append("    IDEA dependencies: [")
    dependencyEnumerator.forEach { orderEntry ->
        debugString?.append("${orderEntry.presentableName} ")
        if (orderEntry.acceptAsDependency(forProduction)) {
            result.addAll(orderEntryToModuleInfo(project, orderEntry, forProduction))
            debugString?.append("OK; ")
        } else {
            debugString?.append("SKIP; ")
        }
        true
    }
    debugString?.appendLine("]")

    return result.toSet()
}

private fun Module.selectSupportedDependencies(
    debugString: StringBuilder?,
    platform: TargetPlatform,
    dependencies: Set<IdeaModuleInfo>
): Set<IdeaModuleInfo> {

    val dependencyFilter = ModuleDependencyFilter(platform, isHMPPEnabled)
    val supportedDependencies = dependencies.filter { dependency -> dependencyFilter.isSupportedDependency(dependency) }.toSet()

    debugString?.appendLine(
        "    Corrected result (Supported dependencies): ${
            supportedDependencies.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ";"
            ) { it.displayedName }
        }"
    )

    return supportedDependencies
}

private fun OrderEntry.acceptAsDependency(forProduction: Boolean): Boolean {
    return this !is ExportableOrderEntry
            || !forProduction
            // this is needed for Maven/Gradle projects with "production-on-test" dependency
            || this is ModuleOrderEntry && isProductionOnTestDependency
            || scope.isForProductionCompile
}

private fun orderEntryToModuleInfo(project: Project, orderEntry: OrderEntry, forProduction: Boolean): List<IdeaModuleInfo> {
    fun Module.toInfos() = correspondingModuleInfos().filter { !forProduction || it is ModuleProductionSourceInfo }

    if (!orderEntry.isValid) return emptyList()

    return when (orderEntry) {
        is ModuleSourceOrderEntry -> {
            orderEntry.getOwnerModule().toInfos()
        }
        is ModuleOrderEntry -> {
            val module = orderEntry.module ?: return emptyList()
            if (forProduction && orderEntry.isProductionOnTestDependency) {
                listOfNotNull(module.testSourceInfo())
            } else {
                module.toInfos()
            }
        }
        is LibraryOrderEntry -> {
            val library = orderEntry.library ?: return listOf()
            createLibraryInfo(project, library)
        }
        is JdkOrderEntry -> {
            val sdk = orderEntry.jdk ?: return listOf()
            listOfNotNull(SdkInfo(project, sdk))
        }
        else -> {
            throw IllegalStateException("Unexpected order entry $orderEntry")
        }
    }
}

internal class ModuleDependencyFilter(
    private val dependeePlatform: TargetPlatform,
    private val isHmppEnabled: Boolean
) {

    data class KlibLibraryGist(val isStdlib: Boolean)

    private fun klibLibraryGistOrNull(info: IdeaModuleInfo): KlibLibraryGist? {
        return if (info is AbstractKlibLibraryInfo) KlibLibraryGist(isStdlib = info.libraryRoot.endsWith(KONAN_STDLIB_NAME))
        else null
    }

    fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        /* Filter only acts on LibraryInfo */
        return if (dependency is LibraryInfo) {
            isSupportedDependency(dependency.platform, klibLibraryGistOrNull(dependency))
        } else true
    }

    fun isSupportedDependency(dependencyPlatform: TargetPlatform, klibLibraryGist: KlibLibraryGist? = null): Boolean {
        return if (isHmppEnabled) isSupportedDependencyHmpp(dependencyPlatform, klibLibraryGist)
        else isSupportedDependencyNonHmpp(dependencyPlatform)
    }

    private fun isSupportedDependencyNonHmpp(dependencyPlatform: TargetPlatform): Boolean {
        return dependeePlatform.isJvm() && dependencyPlatform.isJvm() ||
                dependeePlatform.isJs() && dependencyPlatform.isJs() ||
                dependeePlatform.isNative() && dependencyPlatform.isNative() ||
                dependeePlatform.isCommon() && dependencyPlatform.isCommon()
    }

    private fun isSupportedDependencyHmpp(
        dependencyPlatform: TargetPlatform,
        klibLibraryGist: KlibLibraryGist?
    ): Boolean {
        // HACK: allow depending on stdlib even if platforms do not match
        if (dependeePlatform.isNative() && klibLibraryGist != null && klibLibraryGist.isStdlib) return true

        val platformsWhichAreNotContainedInOther = dependeePlatform.componentPlatforms - dependencyPlatform.componentPlatforms
        if (platformsWhichAreNotContainedInOther.isEmpty()) return true

        // unspecifiedNativePlatform is effectively a wildcard for NativePlatform
        if (platformsWhichAreNotContainedInOther.all { it is NativePlatform } &&
            NativePlatforms.unspecifiedNativePlatform.componentPlatforms.single() in dependencyPlatform.componentPlatforms
        ) return true

        // Allow dependencies from any shared native to any other shared native platform.
        //  This will also include dependencies built by the commonizer with one or more missing targets
        //  The Kotlin Gradle Plugin will decide if the dependency is still used in that case.
        //  Since compiling metadata will be possible with this KLIB, the IDE also analyzes the code with it.
        if (dependeePlatform.isSharedNative() && klibLibraryGist != null && dependencyPlatform.isSharedNative()) return true

        return false
    }

    private fun TargetPlatform.isSharedNative(): Boolean {
        if (this.componentPlatforms.all { it is NativePlatform }) {
            if (this.contains(NativePlatformUnspecifiedTarget)) return true
            return this.componentPlatforms.size > 1
        }
        return false
    }
}




@file:Suppress("unused")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.psi.psiUtil.contains
import com.intellij.openapi.projectRoots.Sdk as OpenapiSdk
import com.intellij.openapi.roots.libraries.Library as OpenapiLibrary

/**
 * Represents the kind of [KaSourceModule].
 *
 * For each module, there may be zero to two non-empty [KaSourceModule]s based on the corresponding source sets (production, test). Each
 * [KaSourceModule] should correspond to exactly one of the kinds.
 */
enum class KaSourceModuleKind {
    /** Production (main) source module. */
    PRODUCTION,

    /** Test source module. */
    TEST;
}

/**
 * A [KaSourceModule] with a [KaSourceModuleKind].
 */
interface KaSourceModuleWithKind : KaSourceModule {
    val kind: KaSourceModuleKind
}

/**
 * Converts the [ModuleId] to a [KaSourceModule] of the given [kind].
 *
 * For each [ModuleId], there may be from 0 to 2 [KaSourceModule] based on the corresponding source sets (production, test)
 *
 * @param kind The kind of source module to retrieve.
 * @return The corresponding [KaSourceModule] if exists, or `null` if not found.
 */
fun ModuleId.toKaSourceModule(project: Project, kind: KaSourceModuleKind): KaSourceModule? =
    project.ideProjectStructureProvider.getKaSourceModule(this, kind)

/**
 * Converts the [ModuleId] to a test [KaSourceModule].
 *
 * @return The corresponding test [KaSourceModule] if exists, or `null` if not found.
 * @see toKaSourceModule
 */
fun ModuleId.toKaSourceModuleForTest(project: Project): KaSourceModule? =
    toKaSourceModule(project, KaSourceModuleKind.TEST)

/**
 * Converts the [ModuleId] to a production [KaSourceModule].
 *
 * @return The corresponding production [KaSourceModule] if exists, or `null` if not found.
 * @see toKaSourceModule
 */
fun ModuleId.toKaSourceModuleForProduction(project: Project): KaSourceModule? =
    toKaSourceModule(project, KaSourceModuleKind.PRODUCTION)


fun ModuleEntity.toKaSourceModule(project: Project, kind: KaSourceModuleKind): KaSourceModule? =
    project.ideProjectStructureProvider.getKaSourceModule(this, kind)


/**
 * Converts the [ModuleId] to either a production or test [KaSourceModule].
 *
 * @return The corresponding production or test [KaSourceModule] if it exists, or `null` if not found. If both exist, the production one is returned.
 */
@Deprecated("Semantics have changed (KTIJ-34177). Use other `toKaSourceModule*` functions instead.", level = DeprecationLevel.ERROR)
fun ModuleId.toKaSourceModuleForProductionOrTest(project: Project): KaSourceModule? {
    val projectStructureProvider = project.ideProjectStructureProvider
    return projectStructureProvider.getKaSourceModule(this, KaSourceModuleKind.PRODUCTION)
        ?: projectStructureProvider.getKaSourceModule(this, KaSourceModuleKind.TEST)
}

/**
 * Converts the [ModuleId] to a list of [KaSourceModule]s containing both production and test modules. If a module doesn't exist, it's not
 * included in the list.
 */
fun ModuleId.toKaSourceModules(project: Project): List<KaSourceModule> =
    project.ideProjectStructureProvider.getKaSourceModules(this)

/**
 * Converts the [Module] to a [KaSourceModule] of the given [kind].
 *
 *For each [Module], there may be from 0 to 2 [KaSourceModule] based on the corresponding source sets (production, test)
 *
 * @return The corresponding [KaSourceModule] if exists, or `null` if not found.
 */
fun Module.toKaSourceModule(kind: KaSourceModuleKind): KaSourceModule? =
    project.ideProjectStructureProvider.getKaSourceModule(this, kind)

/**
 * Converts the [Module] to a test [KaSourceModule].
 *
 * @return The corresponding test [KaSourceModule] if exists, or `null` if not found.
 * @see toKaSourceModule
 */
fun Module.toKaSourceModuleForTest(): KaSourceModule? =
    toKaSourceModule(KaSourceModuleKind.TEST)

/**
 * Converts the [Module] to a production [KaSourceModule].
 *
 * @return The corresponding production [KaSourceModule] if exists, or `null` if not found.
 * @see toKaSourceModule
 */
fun Module.toKaSourceModuleForProduction(): KaSourceModule? =
    toKaSourceModule(KaSourceModuleKind.PRODUCTION)

/**
 * Converts the [Module] to either a production or test [KaSourceModule].
 *
 * @return The corresponding production or test [KaSourceModule] if it exists, or `null` if not found. If both exist, the production one is returned.
 */
@Deprecated("Semantics have changed (KTIJ-34177). Use other `toKaSourceModule*` functions instead.", level = DeprecationLevel.ERROR)
fun Module.toKaSourceModuleForProductionOrTest(): KaSourceModule? {
    val provider = project.ideProjectStructureProvider
    return provider.getKaSourceModule(this, KaSourceModuleKind.PRODUCTION)
        ?: provider.getKaSourceModule(this, KaSourceModuleKind.TEST)
}

/**
 * Converts the [Module] to a list of [KaSourceModule] containing both production and test modules. If a module doesn't exist, it's not
 * included in the list.
 */
fun Module.toKaSourceModules(): List<KaSourceModule> =
    project.ideProjectStructureProvider.getKaSourceModules(this)

/**
 * Converts the [Module] to a [KaSourceModule] of the same kind as the [KaSourceModule] of the given [contextElement].
 *
 * If [contextElement] is not contained in a [KaSourceModule], this function returns the production source module.
 */
fun Module.toKaSourceModuleWithElementSourceModuleKindOrProduction(contextElement: PsiElement): KaSourceModule? {
    val contextSourceModule =
        contextElement.getKaModuleOfTypeSafe<KaSourceModule>(project, useSiteModule = null)
            ?: return toKaSourceModuleForProduction()

    return toKaSourceModule(contextSourceModule.sourceModuleKind)
}

/**
 * Converts the [Module] to the production or test [KaSourceModule] which contains [element].
 *
 * In contrast to [KaModuleProvider.getModule] and [getKaModule], this function works for [PSI directories][com.intellij.psi.PsiDirectory]
 * and should generally be more efficient when the [Module] is already known.
 */
fun Module.toKaSourceModuleContainingElement(element: PsiElement): KaSourceModule? =
    toKaSourceModules().firstOrNull { it.contentScope.contains(element) }

/**
 * Converts the [LibraryId] to a list of [KaLibraryModule] in the specified [project].
 *
 * Each [LibraryId] may correspond to multiple [KaLibraryModule].
 * For example, for a KMP library, we may create a separate [KaLibraryModule] for each target library.
 *
 * @param project The project in which the library resides.
 * @return A list of corresponding [KaLibraryModule].
 */
fun LibraryId.toKaLibraryModules(project: Project): List<KaLibraryModule> =
    project.ideProjectStructureProvider.getKaLibraryModules(this)

fun LibraryEntity.toKaLibraryModules(project: Project): List<KaLibraryModule> =
    project.ideProjectStructureProvider.getKaLibraryModules(this)

val KaSourceModule.symbolicId: ModuleId
    get() = project.ideProjectStructureProvider.getKaSourceModuleSymbolId(this)

val KaLibraryModule.symbolicId: LibraryId
    get() = project.ideProjectStructureProvider.getKaLibraryModuleSymbolicId(this)

val KaSourceModule.sourceModuleKind: KaSourceModuleKind
    get() {
        require(this is KaSourceModuleWithKind) {
            "Expected `${KaSourceModuleWithKind::class.simpleName}`, but got `${this::class.simpleName}` instead."
        }
        return kind
    }

val KaSourceModule.openapiModule: Module
    get() = project.ideProjectStructureProvider.getOpenapiModule(this)

/**
 * Gets the [com.intellij.openapi.roots.libraries.Library] represented by this [KaLibraryModule].
 *
 * @return the [com.intellij.openapi.roots.libraries.Library] that represents the current [KaLibraryModule],
 * or `null` if the current [KaLibraryModule] is an SDK.
 */
val KaLibraryModule.openapiLibrary: OpenapiLibrary?
    get() = project.ideProjectStructureProvider.getOpenapiLibrary(this)

/**
 * Gets the [com.intellij.openapi.projectRoots.Sdk] represented by this [KaLibraryModule].
 *
 * @return the [com.intellij.openapi.projectRoots.Sdk] that represents the current [KaLibraryModule],
 * or `null` if the current [KaLibraryModule] is a library.
 */
val KaLibraryModule.openapiSdk: OpenapiSdk?
    get() = project.ideProjectStructureProvider.getOpenapiSdk(this)

/**
 * Converts the [Library] to a list of [KaLibraryModule] in the specified [project].
 *
 * Each [Library] may correspond to multiple [KaLibraryModule].
 * For example, for a KMP library, we may create a separate [KaLibraryModule] for each target library.
 *
 * @return A list of corresponding [KaLibraryModule].
 */
fun Library.toKaLibraryModules(project: Project): List<KaLibraryModule> =
    project.ideProjectStructureProvider.getKaLibraryModules(this)

/**
 * Converts the [OpenapiSdk] to a list of [KaLibraryModule] in the specified [project].
 *
 * @return A list of corresponding [KaLibraryModule].
 */
fun OpenapiSdk.toKaLibraryModule(project: Project): KaLibraryModule =
    project.ideProjectStructureProvider.getKaLibraryModule(this)

/**
 * Returns a [KaModule] for a given PsiElement in the context of the [useSiteModule].
 *
 * The use-site module is the [KaModule] from which [getKaModule] is called. This concept is the same as the use-site module accepted by
 * [analyze][org.jetbrains.kotlin.analysis.api.analyze], and closely related to the concept of a use-site element. In essence, when we
 * are performing analysis, most of the time we do so from the point of view of a particular [KaModule] or [PsiElement]. If this module
 * is already known, it should be passed as the [useSiteModule] to [getKaModule].
 *
 * @see org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider.getModule
 */
fun PsiElement.getKaModule(project: Project, useSiteModule: KaModule?): KaModule =
    KaModuleProvider.getModule(project, this, useSiteModule)

/**
 * @return [KaModule] for a given PsiElement in the context of the [useSiteModule] if it's of type [M]. Returns `null` otherwise.
 *
 * @see org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider.getModule
 */
inline fun <reified M : KaModule> PsiElement.getKaModuleOfTypeSafe(project: Project, useSiteModule: KaModule?): M? =
    getKaModule(project, useSiteModule) as? M


/**
 * @return [KaModule] for a given PsiElement in the context of the [useSiteModule] if it's of type [M]. Throws an exception otherwise.
 *
 * @see org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider.getModule
 */
inline fun <reified M : KaModule> PsiElement.getKaModuleOfType(project: Project, useSiteModule: KaModule?): M =
    getKaModule(project, useSiteModule) as M


/**
 * Returns a list of [KaModule] instances that correspond to a specific [VirtualFile].
 *
 * In some cases, a virtual file may be contained in multiple [KaModule]s. For example,
 * the same JAR or a class file from that JAR may be reused across different libraries.
 *
 * @return a list of [KaModule]s that use the current [VirtualFile].
 * If a virtual file is not part of a project, an empty list is returned.
 * Thus, it never returns [org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule] as a result.
*/
fun VirtualFile.getAssociatedKaModules(project: Project): List<KaModule> =
    project.ideProjectStructureProvider.getAssociatedKaModules(this)

fun KaLibraryModule.getKotlinLibraries(project: Project): List<KotlinLibrary> =
    project.ideProjectStructureProvider.getKotlinLibraries(this)

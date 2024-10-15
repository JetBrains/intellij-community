@file:Suppress("unused")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule

/**
 * Represents kind of [KaSourceModule]
 *
 * For each module, there may be from 0 to 2 [KaSourceModule] based on the corresponding source sets (production, test)
 * Each [KaSourceModule] should be one of the provided kinds.
 */
enum class KaSourceModuleKind {
    /** Production (main) source module. */
    PRODUCTION,

    /** Test source module. */
    TEST;
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

/**
 * Converts the [ModuleId] to either a production or test [KaSourceModule].
 *
 * @return The corresponding production or test [KaSourceModule] if it exists, or `null` if not found. If both exist, the production one is returned.
 */
fun ModuleId.toKaSourceModuleForProductionOrTest(project: Project): KaSourceModule? {
    val projectStructureProvider = project.ideProjectStructureProvider
    return projectStructureProvider.getKaSourceModule(this, KaSourceModuleKind.PRODUCTION)
        ?: projectStructureProvider.getKaSourceModule(this, KaSourceModuleKind.TEST)
}

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
fun Module.toKaSourceModuleForProductionOrTest(): KaSourceModule? {
    val provider = project.ideProjectStructureProvider
    return provider.getKaSourceModule(this, KaSourceModuleKind.PRODUCTION)
        ?: provider.getKaSourceModule(this, KaSourceModuleKind.TEST)
}

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

val KaSourceModule.symbolicId: ModuleId
    get() = project.ideProjectStructureProvider.getKaSourceModuleSymbolId(this)

val KaLibraryModule.symbolicId: LibraryId
    get() = project.ideProjectStructureProvider.getKaLibraryModuleSymbolicId(this)

val KaSourceModule.sourceModuleKind: KaSourceModuleKind?
    get() = project.ideProjectStructureProvider.getKaSourceModuleKind(this)


val KaSourceModule.openapiModule: Module
    get() = project.ideProjectStructureProvider.getOpenapiModule(this)

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
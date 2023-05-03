// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.fileClasses.JvmMultifileClassPartInfo
import org.jetbrains.kotlin.fileClasses.fileClassInfo
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.BinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.caches.project.binariesScope
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.decompiler.navigation.MemberMatching.*
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.debugText.getDebugText
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.SmartList

object SourceNavigationHelper {
    private val LOG = Logger.getInstance(SourceNavigationHelper::class.java)

    enum class NavigationKind {
        CLASS_FILES_TO_SOURCES,
        SOURCES_TO_CLASS_FILES
    }

    private var forceResolve = false

    @TestOnly
    fun resetForceResolve() {
        forceResolve = false
    }

    @TestOnly
    fun setForceResolve(forceResolve: Boolean) {
        SourceNavigationHelper.forceResolve = forceResolve
    }

    fun targetClassFilesToSourcesScopes(virtualFile: VirtualFile, project: Project): List<GlobalSearchScope> {
        val binaryModuleInfos = ModuleInfoProvider.getInstance(project)
            .collectLibraryBinariesModuleInfos(virtualFile)
            .toList()

        val primaryScope = binaryModuleInfos.mapNotNull { it.sourcesModuleInfo?.sourceScope() }.union()
        val additionalScope = binaryModuleInfos.flatMap {
            it.associatedCommonLibraries() + it.sourcesOnlyDependencies()
        }.mapNotNull { it.sourcesModuleInfo?.sourceScope() }.union()

        return if (binaryModuleInfos.any { it is ScriptDependenciesInfo }) {
            // NOTE: this is a workaround for https://github.com/gradle/gradle/issues/13783:
            // script configuration for *.gradle.kts files doesn't include sources for included plugins
            primaryScope + additionalScope + ProjectScope.getContentScope(project)
        } else {
            primaryScope + additionalScope
        }
    }

    private fun targetScopes(declaration: KtElement, navigationKind: NavigationKind): List<GlobalSearchScope> {
        val containingFile = declaration.containingKtFile
        val vFile = containingFile.virtualFile ?: return emptyList()
        val project = declaration.project
        return when (navigationKind) {
            NavigationKind.CLASS_FILES_TO_SOURCES -> targetClassFilesToSourcesScopes(vFile, project)
            NavigationKind.SOURCES_TO_CLASS_FILES -> {
                if (containingFile.fileClassInfo is JvmMultifileClassPartInfo) {
                    // if the asked element is multifile classs, it might be compiled into .kotlin_metadata and .class
                    // but we don't have support of metadata declarations in light classes and in reference search (without
                    // acceptOverrides). That's why we include only .class jar in the scope.
                    val psiClass = containingFile.findFacadeClass()
                    if (psiClass != null) {
                        return ModuleInfoProvider.getInstance(project)
                            .collectLibraryBinariesModuleInfos(psiClass.containingFile.virtualFile)
                            .map { it.binariesScope }
                            .toSet()
                            .union()
                    }
                }
                ModuleInfoProvider.getInstance(project)
                    .collectLibrarySourcesModuleInfos(vFile)
                    .map { it.binariesModuleInfo.binariesScope }
                    .toList()
                    .union()
            }
        }
    }

    private fun BinaryModuleInfo.associatedCommonLibraries(): List<BinaryModuleInfo> {
        if (platform.isCommon()) return emptyList()

        val result = SmartList<BinaryModuleInfo>()
        val dependencies = dependencies()
        for (ideaModuleInfo in dependencies) {
            if (ideaModuleInfo is BinaryModuleInfo && ideaModuleInfo.platform.isCommon()) {
                result += ideaModuleInfo
            }
        }
        return result
    }

    private fun BinaryModuleInfo.sourcesOnlyDependencies(): List<BinaryModuleInfo> {
        if (this !is LibraryInfo) return emptyList()

        return LibraryDependenciesCache.getInstance(project).getLibraryDependencies(this).sourcesOnlyDependencies
    }

    private fun Collection<GlobalSearchScope>.union(): List<GlobalSearchScope> =
        if (this.isNotEmpty()) listOf(GlobalSearchScope.union(this)) else emptyList()

    private fun haveRenamesInImports(files: Collection<KtFile>) = files.any { file -> file.importDirectives.any { it.aliasName != null } }

    private fun findSpecialProperty(memberName: Name, containingClass: KtClass): KtNamedDeclaration? {
        // property constructor parameters
        val constructorParameters = containingClass.primaryConstructorParameters
        for (constructorParameter in constructorParameters) {
            if (memberName == constructorParameter.nameAsName && constructorParameter.hasValOrVar()) {
                return constructorParameter
            }
        }

        // enum entries
        if (containingClass.hasModifier(KtTokens.ENUM_KEYWORD)) {
            for (declaration in containingClass.declarations) {
                if (declaration is KtEnumEntry && declaration.nameAsSafeName == memberName) {
                    return declaration
                }
            }
        }
        return null
    }

    private fun convertPropertyOrFunction(
        declaration: KtNamedDeclaration,
        navigationKind: NavigationKind
    ): KtNamedDeclaration? {
        if (declaration is KtPrimaryConstructor) {
            val sourceClassOrObject = findClassOrObject(declaration.getContainingClassOrObject(), navigationKind)
            return sourceClassOrObject?.primaryConstructor ?: sourceClassOrObject
        }

        val memberNameAsString = declaration.name ?: run {
            LOG.debug("Declaration with null name:" + declaration.getDebugText())
            return null
        }

        val memberName = Name.identifier(memberNameAsString)

        val decompiledContainer = declaration.parent

        var candidates: Collection<KtNamedDeclaration>
        when (decompiledContainer) {
            is KtFile -> candidates = getInitialTopLevelCandidates(declaration, navigationKind)
            is KtClassBody -> {
                val decompiledClassOrObject = decompiledContainer.getParent() as KtClassOrObject
                val sourceClassOrObject = findClassOrObject(decompiledClassOrObject, navigationKind)

                candidates = sourceClassOrObject?.let {
                    getInitialMemberCandidates(sourceClassOrObject, memberName, declaration::class.java)
                }.orEmpty()

                if (candidates.isEmpty()) {
                    if (declaration is KtProperty && sourceClassOrObject is KtClass) {
                        return findSpecialProperty(memberName, sourceClassOrObject)
                    }
                }
            }

            else -> throw KotlinExceptionWithAttachments("Unexpected container of ${if (navigationKind == NavigationKind.CLASS_FILES_TO_SOURCES) "decompiled" else "source"} declaration: ${decompiledContainer::class.java.simpleName}")
                .withPsiAttachment("declaration", declaration)
                .withPsiAttachment("container", decompiledContainer)
                .withPsiAttachment("file", declaration.containingFile)
        }

        if (candidates.isEmpty()) {
            return null
        }

        if (!forceResolve) {
            ProgressManager.checkCanceled()

            candidates = candidates.filter { sameReceiverPresenceAndParametersCount(it, declaration) }

            if (candidates.size <= 1) {
                return candidates.firstOrNull()
            }

            if (!haveRenamesInImports(candidates.getContainingFiles())) {
                ProgressManager.checkCanceled()

                candidates = candidates.filter { receiverAndParametersShortTypesMatch(it, declaration) }


                if (candidates.size <= 1) {
                    return candidates.firstOrNull()
                }
            }
        }

        for (candidate in candidates) {
            ProgressManager.checkCanceled()

            val candidateDescriptor = candidate.resolveToDescriptorIfAny() as? CallableDescriptor ?: continue
            if (receiversMatch(declaration, candidateDescriptor)
                && valueParametersTypesMatch(declaration, candidateDescriptor)
                && typeParametersMatch(declaration as KtTypeParameterListOwner, candidateDescriptor.typeParameters)
            ) {
                return candidate
            }
        }

        return null
    }

    private fun <T : KtNamedDeclaration> findFirstMatchingInIndex(
        entity: T,
        navigationKind: NavigationKind,
        index: StringStubIndexExtension<T>
    ): T? {
        val classFqName = entity.fqName ?: return null
        return targetScopes(entity, navigationKind).firstNotNullOfOrNull { scope ->
            ProgressManager.checkCanceled()
            index.get(classFqName.asString(), entity.project, scope).minByOrNull { it.isExpectDeclaration() }
        }
    }

    private fun findClassOrObject(decompiledClassOrObject: KtClassOrObject, navigationKind: NavigationKind): KtClassOrObject? =
        findFirstMatchingInIndex(decompiledClassOrObject, navigationKind, KotlinFullClassNameIndex)

    private fun getInitialTopLevelCandidates(
        declaration: KtNamedDeclaration,
        navigationKind: NavigationKind
    ): Collection<KtNamedDeclaration> {
        val scopes = targetScopes(declaration, navigationKind)

        val index: StringStubIndexExtension<out KtNamedDeclaration> = when (declaration) {
            is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex
            is KtProperty -> KotlinTopLevelPropertyFqnNameIndex
            else -> throw IllegalArgumentException("Neither function nor declaration: " + declaration::class.java.name)
        }

        return scopes.flatMap { scope ->
            ProgressManager.checkCanceled()
            index.get(declaration.fqName!!.asString(), declaration.project, scope).sortedBy { it.isExpectDeclaration() }
        }
    }

    private fun getInitialMemberCandidates(
        sourceClassOrObject: KtClassOrObject,
        name: Name,
        declarationClass: Class<out KtNamedDeclaration>
    ): List<KtNamedDeclaration> {
        val result = SmartList<KtNamedDeclaration>()
        for (declaration in sourceClassOrObject.declarations) {
            if (declarationClass.isInstance(declaration) && name == (declaration as? KtNamedDeclaration)?.nameAsSafeName) {
                result += declaration
            }
        }
        return result
    }

    fun getNavigationElement(declaration: KtDeclaration): KtDeclaration {
        return navigateToDeclaration(declaration, NavigationKind.CLASS_FILES_TO_SOURCES)
    }

    fun getOriginalElement(declaration: KtDeclaration): KtDeclaration {
        return navigateToDeclaration(declaration, NavigationKind.SOURCES_TO_CLASS_FILES)
    }

    private fun navigateToDeclaration(
        from: KtDeclaration,
        navigationKind: NavigationKind
    ): KtDeclaration {
        val project = from.project
        if (DumbService.isDumb(project)) return from

        when (navigationKind) {
            NavigationKind.CLASS_FILES_TO_SOURCES -> if (!from.containingKtFile.isCompiled) return from
            NavigationKind.SOURCES_TO_CLASS_FILES -> {
                val file = from.containingFile
                if (file is KtFile && file.isCompiled) return from
                if (!RootKindFilter.librarySources.matches(from)) return from
                if (KtPsiUtil.isLocal(from)) return from
            }
        }

        return from.accept(SourceAndDecompiledConversionVisitor(navigationKind), Unit) ?: from
    }

    private class SourceAndDecompiledConversionVisitor(private val navigationKind: NavigationKind) : KtVisitor<KtDeclaration?, Unit>() {

        override fun visitNamedFunction(function: KtNamedFunction, data: Unit) = convertPropertyOrFunction(function, navigationKind)

        override fun visitProperty(property: KtProperty, data: Unit) = convertPropertyOrFunction(property, navigationKind)

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration, data: Unit) = findClassOrObject(declaration, navigationKind)

        override fun visitClass(klass: KtClass, data: Unit) = findClassOrObject(klass, navigationKind)

        override fun visitTypeAlias(typeAlias: KtTypeAlias, data: Unit) =
            findFirstMatchingInIndex(typeAlias, navigationKind, KotlinTopLevelTypeAliasFqNameIndex)

        override fun visitParameter(parameter: KtParameter, data: Unit): KtDeclaration? {
            val callableDeclaration = parameter.parents.match(KtParameterList::class, last = KtCallableDeclaration::class)
                ?: error("Can't typeMatch ${parameter.parent.parent}")
            val parameters = callableDeclaration.valueParameters
            val index = parameters.indexOf(parameter)

            val sourceCallable = callableDeclaration.accept(this, Unit) as? KtCallableDeclaration ?: return null
            val sourceParameters = sourceCallable.valueParameters
            if (sourceParameters.size != parameters.size) return null
            return sourceParameters[index]
        }

        override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor, data: Unit) =
            convertPropertyOrFunction(constructor, navigationKind)

        override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, data: Unit) =
            convertPropertyOrFunction(constructor, navigationKind)
    }
}

private fun Collection<KtNamedDeclaration>.getContainingFiles(): Collection<KtFile> = mapNotNullTo(LinkedHashSet()) {
    it.containingFile as? KtFile
}

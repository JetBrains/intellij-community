// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.imports.getImportableTargets
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.project.findAnalyzerServices
import org.jetbrains.kotlin.idea.refactoring.fqName.isImported
import org.jetbrains.kotlin.idea.resolve.getLanguageVersionSettings
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.*
import org.jetbrains.kotlin.resolve.scopes.utils.*
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider
import org.jetbrains.kotlin.utils.addIfNotNull

class ImportInsertHelperImpl(private val project: Project) : ImportInsertHelper() {
    private fun getCodeStyleSettings(contextFile: KtFile): KotlinCodeStyleSettings = contextFile.kotlinCustomSettings

    override fun getImportSortComparator(contextFile: KtFile): Comparator<ImportPath> = ImportPathComparator(
        getCodeStyleSettings(contextFile).PACKAGES_IMPORT_LAYOUT
    )

    override fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean {
        val languageVersionSettings = contextFile.getResolutionFacade().getLanguageVersionSettings()
        val platform = TargetPlatformDetector.getPlatform(contextFile)
        val analyzerServices = platform.findAnalyzerServices(contextFile.project)
        val allDefaultImports = analyzerServices.getDefaultImports(languageVersionSettings, includeLowPriorityImports = true)

        val scriptExtraImports = contextFile.takeIf { it.isScript() }?.let { ktFile ->
            val scriptDependencies = ScriptDependenciesProvider.getInstance(ktFile.project)
                ?.getScriptConfiguration(ktFile.originalFile as KtFile)
            scriptDependencies?.defaultImports?.map { ImportPath.fromString(it) }
        }.orEmpty()

        return importPath.isImported(allDefaultImports + scriptExtraImports, analyzerServices.excludedImports)
    }

    override fun isImportedWithLowPriorityDefaultImport(importPath: ImportPath, contextFile: KtFile): Boolean {
        val platform = TargetPlatformDetector.getPlatform(contextFile)
        val analyzerServices = platform.findAnalyzerServices(contextFile.project)
        return importPath.isImported(analyzerServices.defaultLowPriorityImports, analyzerServices.excludedImports)
    }

    override fun mayImportOnShortenReferences(descriptor: DeclarationDescriptor, contextFile: KtFile): Boolean {
        return when (descriptor.getImportableDescriptor()) {
            is PackageViewDescriptor -> false // now package cannot be imported

            is ClassDescriptor -> {
                descriptor.getImportableDescriptor().containingDeclaration is PackageFragmentDescriptor || getCodeStyleSettings(contextFile).IMPORT_NESTED_CLASSES
            }

            else -> descriptor.getImportableDescriptor().containingDeclaration is PackageFragmentDescriptor // do not import members (e.g. java static members)
        }
    }

    override fun importDescriptor(
        element: KtElement,
        descriptor: DeclarationDescriptor,
        actionRunningMode: ActionRunningMode,
        forceAllUnderImport: Boolean
    ): ImportDescriptorResult {
        val importer = Importer(element, actionRunningMode)
        return if (forceAllUnderImport) {
            importer.importDescriptorWithStarImport(descriptor)
        } else {
            importer.importDescriptor(descriptor)
        }
    }

    override fun importPsiClass(element: KtElement, psiClass: PsiClass, actionRunningMode: ActionRunningMode): ImportDescriptorResult {
        return Importer(element, actionRunningMode).importPsiClass(psiClass)
    }

    private inner class Importer(
        private val element: KtElement,
        private val actionRunningMode: ActionRunningMode
    ) {
        private val file = element.containingKtFile
        private val resolutionFacade = file.getResolutionFacade()

        private fun alreadyImported(target: DeclarationDescriptor, scope: LexicalScope, targetFqName: FqName): ImportDescriptorResult? {
            val name = target.name
            return when (target) {
                is ClassifierDescriptorWithTypeParameters -> {
                    val classifiers = scope.findClassifiers(name, NoLookupLocation.FROM_IDE)
                        .takeIf { it.isNotEmpty() } ?: return null
                    val classifier: ClassifierDescriptor =
                        if (classifiers.all { it is TypeAliasDescriptor }) {
                            classifiers.singleOrNull() ?: return null
                        } else {
                            // kotlin.collections.ArrayList is not a conflict, it's an alias to java.util.ArrayList
                            val classifiers2 = classifiers.filter { it !is TypeAliasDescriptor }
                            // top-level classifiers could/should be resolved with imports
                            if (classifiers2.size > 1 && classifiers2.all { it.containingDeclaration is PackageFragmentDescriptor }) {
                                return null
                            }
                            classifiers2.singleOrNull() ?: return ImportDescriptorResult.FAIL
                        }
                    ImportDescriptorResult.ALREADY_IMPORTED.takeIf { classifier.importableFqName == targetFqName }
                }
                is FunctionDescriptor ->
                    ImportDescriptorResult.ALREADY_IMPORTED.takeIf { scope.findFunction(name, NoLookupLocation.FROM_IDE) { it.importableFqName == targetFqName } != null }
                is PropertyDescriptor ->
                    ImportDescriptorResult.ALREADY_IMPORTED.takeIf { scope.findVariable(name, NoLookupLocation.FROM_IDE) { it.importableFqName == targetFqName } != null }
                else -> null
            }
        }

        private fun HierarchicalScope.findClassifiers(name: Name, location: LookupLocation): Set<ClassifierDescriptor> {
            val result = mutableSetOf<ClassifierDescriptor>()
            processForMeAndParent { it.getContributedClassifier(name, location)?.let(result::add) }
            return result
        }

        fun importPsiClass(psiClass: PsiClass): ImportDescriptorResult {
            val qualifiedName = psiClass.qualifiedName!!

            val targetFqName = FqName(qualifiedName)
            val name = Name.identifier(psiClass.name!!)

            val scope = if (element == file) resolutionFacade.getFileResolutionScope(file) else element.getResolutionScope()

            scope.findClassifier(name, NoLookupLocation.FROM_IDE)?.let {
                return if (it.fqNameSafe == targetFqName) ImportDescriptorResult.ALREADY_IMPORTED else ImportDescriptorResult.FAIL
            }

            val imports = file.importDirectives

            if (imports.any { !it.isAllUnder && (it.importPath?.alias == name || it.importPath?.fqName == targetFqName) }) {
                return ImportDescriptorResult.FAIL
            }

            addImport(targetFqName, false)

            return ImportDescriptorResult.IMPORT_ADDED
        }

        fun importDescriptor(descriptor: DeclarationDescriptor): ImportDescriptorResult {
            val target = descriptor.getImportableDescriptor()

            val name = target.name
            val topLevelScope = resolutionFacade.getFileResolutionScope(file)

            // check if import is not needed
            val targetFqName = target.importableFqName ?: return ImportDescriptorResult.FAIL

            val scope = if (element == file) topLevelScope else element.getResolutionScope()

            alreadyImported(target, scope, targetFqName)?.let { return it }

            val imports = file.importDirectives

            if (imports.any { !it.isAllUnder && it.importPath?.fqName == targetFqName }) {
                return ImportDescriptorResult.FAIL
            }

            // check there is an explicit import of a class/package with the same name already
            val conflict = when (target) {
                is ClassDescriptor -> topLevelScope.findClassifier(name, NoLookupLocation.FROM_IDE)
                is PackageViewDescriptor -> topLevelScope.findPackage(name)
                else -> null
            }
            if (conflict != null && imports.any {
                    !it.isAllUnder && it.importPath?.fqName == conflict.importableFqName && it.importPath?.importedName == name
                }
            ) {
                return ImportDescriptorResult.FAIL
            }

            val fqName = target.importableFqName!!
            val containerFqName = fqName.parent()

            val tryStarImport = shouldTryStarImport(containerFqName, target, imports) && when (target) {
                // this check does not give a guarantee that import with * will import the class - for example,
                // there can be classes with conflicting name in more than one import with *
                is ClassifierDescriptorWithTypeParameters -> topLevelScope.findClassifier(name, NoLookupLocation.FROM_IDE) == null
                is FunctionDescriptor, is PropertyDescriptor -> true
                else -> error("Unknown kind of descriptor to import:$target")
            }

            if (tryStarImport) {
                val result = addStarImport(target)
                if (result != ImportDescriptorResult.FAIL) return result
            }

            return addExplicitImport(target)
        }

        fun importDescriptorWithStarImport(descriptor: DeclarationDescriptor): ImportDescriptorResult {
            val target = descriptor.getImportableDescriptor()

            val fqName = target.importableFqName ?: return ImportDescriptorResult.FAIL
            val containerFqName = fqName.parent()
            val imports = file.importDirectives

            val starImportPath = ImportPath(containerFqName, true)
            if (imports.any { it.importPath == starImportPath }) {
                return alreadyImported(target, resolutionFacade.getFileResolutionScope(file), fqName) ?: ImportDescriptorResult.FAIL
            }

            if (!canImportWithStar(containerFqName, target)) return ImportDescriptorResult.FAIL

            return addStarImport(target)
        }

        private fun shouldTryStarImport(
            containerFqName: FqName,
            target: DeclarationDescriptor,
            imports: Collection<KtImportDirective>,
        ): Boolean {
            if (!canImportWithStar(containerFqName, target)) return false

            val starImportPath = ImportPath(containerFqName, true)
            if (imports.any { it.importPath == starImportPath }) return false

            val codeStyle = getCodeStyleSettings(file)
            if (containerFqName.asString() in codeStyle.PACKAGES_TO_USE_STAR_IMPORTS) return true

            val importsFromPackage = imports.count {
                val path = it.importPath
                path != null && !path.isAllUnder && !path.hasAlias() && path.fqName.parent() == containerFqName
            }

            val nameCountToUseStar = if (target.containingDeclaration is ClassDescriptor)
                codeStyle.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS
            else
                codeStyle.NAME_COUNT_TO_USE_STAR_IMPORT

            return importsFromPackage + 1 >= nameCountToUseStar
        }

        private fun canImportWithStar(containerFqName: FqName, target: DeclarationDescriptor): Boolean {
            if (containerFqName.isRoot) return false

            val container = target.containingDeclaration
            if (container is ClassDescriptor && container.kind == ClassKind.OBJECT) return false // cannot import with '*' from object

            return true
        }

        private fun addStarImport(targetDescriptor: DeclarationDescriptor): ImportDescriptorResult {
            val targetFqName = targetDescriptor.importableFqName!!
            val parentFqName = targetFqName.parent()

            val moduleDescriptor = resolutionFacade.moduleDescriptor
            val scopeToImport = getMemberScope(parentFqName, moduleDescriptor) ?: return ImportDescriptorResult.FAIL

            val filePackage = moduleDescriptor.getPackage(file.packageFqName)

            fun isVisible(descriptor: DeclarationDescriptor): Boolean {
                if (descriptor !is DeclarationDescriptorWithVisibility) return true
                val visibility = descriptor.visibility
                return !visibility.mustCheckInImports() || DescriptorVisibilities.isVisibleIgnoringReceiver(descriptor, filePackage)
            }

            val kindFilter = DescriptorKindFilter.ALL.withoutKinds(DescriptorKindFilter.PACKAGES_MASK)
            val allNamesToImport = scopeToImport.getDescriptorsFiltered(kindFilter).filter(::isVisible).map { it.name }.toSet()

            fun targetFqNameAndType(ref: KtReferenceExpression): Pair<FqName, Class<out Any>>? {
                val descriptors = ref.resolveTargets()
                val fqName: FqName? = descriptors.filter(::isVisible).map { it.importableFqName }.toSet().singleOrNull()
                return if (fqName != null) {
                    Pair(fqName, descriptors.elementAt(0).javaClass)
                } else null
            }

            val futureCheckMap = HashMap<KtSimpleNameExpression, Pair<FqName, Class<out Any>>>()
            file.accept(object : KtVisitorVoid() {
                override fun visitElement(element: PsiElement): Unit = element.acceptChildren(this)
                override fun visitImportList(importList: KtImportList) {}
                override fun visitPackageDirective(directive: KtPackageDirective) {}
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    val refName = expression.getReferencedNameAsName()
                    if (allNamesToImport.contains(refName)) {
                        val target = targetFqNameAndType(expression)
                        if (target != null) {
                            futureCheckMap += Pair(expression, target)
                        }
                    }
                }
            })

            val addedImport = addImport(parentFqName, true)

            if (alreadyImported(targetDescriptor, resolutionFacade.getFileResolutionScope(file), targetFqName) == null) {
                actionRunningMode.runAction { addedImport.delete() }
                return ImportDescriptorResult.FAIL
            }
            dropRedundantExplicitImports(parentFqName)

            val conflicts = futureCheckMap
                .mapNotNull { (expr, fqNameAndType) ->
                    if (targetFqNameAndType(expr) != fqNameAndType) fqNameAndType.first else null
                }
                .toSet()

            fun isNotImported(fqName: FqName): Boolean {
                return file.importDirectives.none { directive ->
                    !directive.isAllUnder && directive.alias == null && directive.importedFqName == fqName
                }
            }

            for (conflict in conflicts.filter(::isNotImported)) {
                addImport(conflict, false)
            }

            return ImportDescriptorResult.IMPORT_ADDED
        }

        private fun getMemberScope(fqName: FqName, moduleDescriptor: ModuleDescriptor): MemberScope? {
            val packageView = moduleDescriptor.getPackage(fqName)
            if (!packageView.isEmpty()) {
                return packageView.memberScope
            }

            val parentScope = getMemberScope(fqName.parent(), moduleDescriptor) ?: return null
            val classifier = parentScope.getContributedClassifier(fqName.shortName(), NoLookupLocation.FROM_IDE)
            val classDescriptor = classifier as? ClassDescriptor ?: return null
            return classDescriptor.defaultType.memberScope
        }

        private fun addExplicitImport(target: DeclarationDescriptor): ImportDescriptorResult {
            if (target is ClassDescriptor || target is PackageViewDescriptor) {
                val topLevelScope = resolutionFacade.getFileResolutionScope(file)
                val name = target.name

                // check if there is a conflicting class imported with * import
                // (not with explicit import - explicit imports are checked before this method invocation)
                val classifier = topLevelScope.findClassifier(name, NoLookupLocation.FROM_IDE)
                if (classifier != null && detectNeededImports(listOf(classifier)).isNotEmpty()) {
                    return ImportDescriptorResult.FAIL
                }
            }

            addImport(target.importableFqName!!, false)
            return ImportDescriptorResult.IMPORT_ADDED
        }

        private fun dropRedundantExplicitImports(packageFqName: FqName) {
            val dropCandidates = file.importDirectives.filter {
                !it.isAllUnder && it.aliasName == null && it.importPath?.fqName?.parent() == packageFqName
            }

            val importsToCheck = ArrayList<FqName>()
            for (import in dropCandidates) {
                if (import.importedReference == null) continue
                val targets = import.targetDescriptors()
                if (targets.any { it is PackageViewDescriptor }) continue // do not drop import of package
                val classDescriptor = targets.filterIsInstance<ClassDescriptor>().firstOrNull()
                importsToCheck.addIfNotNull(classDescriptor?.importableFqName)
                actionRunningMode.runAction { import.delete() }
            }

            if (importsToCheck.isNotEmpty()) {
                val topLevelScope = resolutionFacade.getFileResolutionScope(file)
                for (classFqName in importsToCheck) {
                    val classifier = topLevelScope.findClassifier(classFqName.shortName(), NoLookupLocation.FROM_IDE)
                    if (classifier?.importableFqName != classFqName) {
                        addImport(classFqName, false) // restore explicit import
                    }
                }
            }
        }

        private fun detectNeededImports(importedClasses: Collection<ClassifierDescriptor>): Set<ClassifierDescriptor> {
            if (importedClasses.isEmpty()) return setOf()

            val classesToCheck = importedClasses.associateByTo(mutableMapOf()) { it.name }
            val result = LinkedHashSet<ClassifierDescriptor>()
            file.accept(object : KtVisitorVoid() {
                override fun visitElement(element: PsiElement) {
                    if (classesToCheck.isEmpty()) return
                    element.acceptChildren(this)
                }

                override fun visitImportList(importList: KtImportList) {
                }

                override fun visitPackageDirective(directive: KtPackageDirective) {
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (KtPsiUtil.isSelectorInQualified(expression)) return

                    val refName = expression.getReferencedNameAsName()
                    val descriptor = classesToCheck[refName]
                    if (descriptor != null) {
                        val targetFqName = targetFqName(expression)
                        if (targetFqName != null && targetFqName == DescriptorUtils.getFqNameSafe(descriptor)) {
                            classesToCheck.remove(refName)
                            result.add(descriptor)
                        }
                    }
                }
            })
            return result
        }

        private fun targetFqName(ref: KtReferenceExpression): FqName? =
            ref.resolveTargets().map { it.importableFqName }.toSet().singleOrNull()

        private fun KtReferenceExpression.resolveTargets(): Collection<DeclarationDescriptor> =
            this.getImportableTargets(resolutionFacade.analyze(this, BodyResolveMode.PARTIAL))

        private fun addImport(fqName: FqName, allUnder: Boolean): KtImportDirective = actionRunningMode.runAction {
            addImport(project, file, fqName, allUnder)
        }
    }

    companion object {
        fun addImport(project: Project, file: KtFile, fqName: FqName, allUnder: Boolean = false, alias: Name? = null): KtImportDirective {
            val importPath = ImportPath(fqName, allUnder, alias)

            val psiFactory = KtPsiFactory(project)
            if (file is KtCodeFragment) {
                val newDirective = psiFactory.createImportDirective(importPath)
                file.addImportsFromString(newDirective.text)
                return newDirective
            }

            val importList = file.importList
            if (importList != null) {
                val newDirective = psiFactory.createImportDirective(importPath)
                val imports = importList.imports
                return if (imports.isEmpty()) { //TODO: strange hack
                    importList.add(psiFactory.createNewLine())
                    importList.add(newDirective) as KtImportDirective
                } else {
                    val importPathComparator = ImportInsertHelperImpl(project).getImportSortComparator(file)
                    val insertAfter = imports.lastOrNull {
                        val directivePath = it.importPath
                        directivePath != null && importPathComparator.compare(directivePath, importPath) <= 0
                    }

                    importList.addAfter(newDirective, insertAfter) as KtImportDirective
                }
            } else {
                error("Trying to insert import $fqName into a file ${file.name} of type ${file::class.java} with no import list.")
            }
        }
    }
}

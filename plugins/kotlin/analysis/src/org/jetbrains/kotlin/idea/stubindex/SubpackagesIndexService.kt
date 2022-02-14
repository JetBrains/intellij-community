// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.caches.project.cacheByProvider
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceScope
import org.jetbrains.kotlin.idea.caches.trackers.KotlinPackageModificationListener
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.Collections

class SubpackagesIndexService(private val project: Project): Disposable {

    private val enableSubpackageCaching = Registry.`is`("kotlin.cache.top.level.subpackages", true)

    private val cachedValue = CachedValuesManager.getManager(project).createCachedValue(
        {
            val packageTracker = KotlinPackageModificationListener.getInstance(project).packageTracker
            val dependencies = arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                otherLanguagesModificationTracker(),
                packageTracker
            )
            CachedValueProvider.Result(
                SubpackagesIndex(KotlinPartialPackageNamesIndex.findAllFqNames(project),
                                 dependencies,
                                 packageTracker.modificationCount),
                *dependencies,
            )
        },
        false
    )

    @Volatile
    private var otherLanguagesModificationTracker: ModificationTracker? = null

    init {
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                resetOtherLanguagesModificationTracker()
            }

            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                resetOtherLanguagesModificationTracker()
            }
        })
    }

    private fun resetOtherLanguagesModificationTracker() {
        otherLanguagesModificationTracker = null
    }

    override fun dispose() {
        resetOtherLanguagesModificationTracker()
    }

    private fun otherLanguagesModificationTracker(): ModificationTracker =
        otherLanguagesModificationTracker ?: synchronized(this) {
            otherLanguagesModificationTracker ?: run {
                OtherLanguagesModificationTracker(project).also { otherLanguagesModificationTracker = it }
            }
        }

    inner class SubpackagesIndex(
        allPackageFqNames: Collection<FqName>,
        private val dependencies: Array<ModificationTracker>,
        private val ptCount: Long
    ) {
        // a map from any existing package (in kotlin) to a set of subpackages (not necessarily direct) containing files
        private val allPackageFqNames = hashSetOf<FqName>()
        private val fqNameByPrefix = MultiMap.createSet<FqName, FqName>()

        init {
            this.allPackageFqNames.addAll(allPackageFqNames)
            for (fqName in allPackageFqNames) {
                for (prefix in generateSequence(fqName.parentOrNull(), FqName::parentOrNull)) {
                    fqNameByPrefix.putValue(prefix, fqName)
                }
            }
        }

        /**
         * Known set of partial fqNames of scope.
         * Note: exact matching is a corner case of partial.
         *
         * Null when it is not possible to get and cache fqNames for the scope
         */
        private fun knownPartialFqNames(scope: GlobalSearchScope): Set<FqName>? {
            if (!enableSubpackageCaching) return null

            val moduleSourceScope = scope.safeAs<ModuleSourceScope>() ?: return null
            val map = moduleSourceScope.module.cacheByProvider(*dependencies, provider = ::knownPartialFqNamesProvider)
            val scopeClass = moduleSourceScope.javaClass
            val result = map.computeIfAbsent(scopeClass) {
                KotlinPartialPackageNamesIndex.filterFqNames(allPackageFqNames, scope)
            }
            // result has to have at least `<root>` element, if it is empty it means nothing is indexed in a scope, no reasons to cache it
            return result.ifEmpty {
                map.remove(scopeClass)
                null
            }
        }

        /**
         * Return true if exists package with exact [fqName] OR there are some subpackages of [fqName]
         */
        fun packageExists(fqName: FqName): Boolean = fqName in allPackageFqNames

        /**
         * Return true if package [fqName] exists or some subpackages of [fqName] exist in [scope]
         */
        fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean {
            if (!packageExists(fqName)) {
                return false
            }

            return knownPartialFqNames(scope)?.let { fqName in it } ?: PackageIndexUtil.containsFilesWithPartialPackage(fqName, scope)
        }

        /**
         * Return all direct subpackages of package [fqName].
         *
         * I.e. if there are packages `a.b`, `a.b.c`, `a.c`, `a.c.b` for `fqName` = `a` it returns
         * `a.b` and `a.c`
         *
         * Follow the contract of [com.intellij.psi.PsiElementFinder#getSubPackages]
         */
        fun getSubpackages(fqName: FqName, scope: GlobalSearchScope, nameFilter: (Name) -> Boolean): Collection<FqName> {
            if (!packageExists(fqName)) {
                return emptyList()
            }
            val fqNames = fqNameByPrefix[fqName].ifEmpty { return emptyList() }

            val knownPartialFqNames = knownPartialFqNames(scope)
            knownPartialFqNames?.let {
                if (fqName !in it) return emptyList()
            }

            val subPackagesNames = hashSetOf<Name>()
            val len =  fqName.pathSegmentsSize()
            for (filesFqName in fqNames) {
                ProgressManager.checkCanceled()

                val subPackageName = filesFqName.pathSegments()[len]
                if (subPackageName in subPackagesNames || !nameFilter(subPackageName)) continue

                if (knownPartialFqNames?.contains(filesFqName) == false) {
                    continue
                }

                val containsFilesWithPartialPackage = PackageIndexUtil.containsFilesWithPartialPackage(filesFqName, scope)
                if (containsFilesWithPartialPackage) {
                    subPackagesNames.add(subPackageName)
                }
            }

            return subPackagesNames.map { fqName.child(it) }
        }

        override fun toString() = "SubpackagesIndex: PTC on creation $ptCount, all packages size ${allPackageFqNames.size}"
    }

    companion object {
        fun getInstance(project: Project): SubpackagesIndex {
            return project.getServiceSafe<SubpackagesIndexService>().cachedValue.value!!
        }
    }
}
private fun FqName.pathSegmentsSize() = if (isRoot) 0 else asString().count { it == '.' } + 1

// to avoid capture of extra field (esp. `dependencies`) into a provider lambda
private fun knownPartialFqNamesProvider() = Collections.synchronizedMap(mutableMapOf<Class<out ModuleSourceScope>, Set<FqName>>())

private class OtherLanguagesModificationTracker(val project: Project): ModificationTracker {
    private val delegate = PsiModificationTracker.SERVICE.getInstance(project).forLanguages {
        // PSI changes of Kotlin and Java languages are covered by [KotlinPackageModificationListener]
        // changes in other languages could affect packages
        !it.`is`(Language.ANY) && !it.`is`(KotlinLanguage.INSTANCE) && !it.`is`(JavaLanguage.INSTANCE)
    }

    override fun getModificationCount(): Long = delegate.modificationCount

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OtherLanguagesModificationTracker

        return project == other.safeAs<OtherLanguagesModificationTracker>()?.project
    }

    override fun hashCode(): Int {
        return project.hashCode()
    }

}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.caches.project.StrongCachedValue
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceScope
import org.jetbrains.kotlin.idea.caches.trackers.KotlinPackageModificationListener
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Responsible for Kotlin package only
 */
class SubpackagesIndexService(private val project: Project): Disposable {

    private val enableSubpackageCaching = Registry.`is`("kotlin.cache.top.level.subpackages")

    private val cachedValue =
        StrongCachedValue(
            {
                val packageTracker =
                    KotlinPackageModificationListener.getInstance(project).packageTracker
                val dependencies = arrayOf(
                    ProjectRootModificationTracker.getInstance(project),
                    packageTracker
                )
                val result: CachedValueProvider.Result<SubpackagesIndex> =
                    CachedValueProvider.Result(
                        SubpackagesIndex(KotlinPartialPackageNamesIndex.findAllFqNames(project), packageTracker.modificationCount),
                        *dependencies,
                    )
                result
            })

    init {
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                clean()
            }

            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                clean()
            }
        })
        LowMemoryWatcher.register(::clean, this)
    }

    private fun clean() {
        cachedValue.clear()
    }

    override fun dispose() {
        clean()
    }

    inner class SubpackagesIndex(allPackageFqNames: Collection<FqName>, private val ptCount: Long) {
        // a map from any existing package (in kotlin) to a set of subpackages (not necessarily direct) containing files
        private val allPackageFqNames = hashSetOf<FqName>()
        private val topLevelPackageFqNames = hashSetOf<FqName>()
        private val fqNameByPrefix = MultiMap.createSet<FqName, FqName>()
        // SubpackagesIndex is cachedValue, therefore when we're low on memory - entire SubpackagesIndex will be freed, no reasons to
        // cache individual knownTopFqNames as cachedValue
        private val knownTopFqNamesPerModule = ConcurrentHashMap<Module, MutableMap<Class<out ModuleSourceScope>, Collection<FqName>>>()

        init {
            this.allPackageFqNames.addAll(allPackageFqNames)
            if (allPackageFqNames.isNotEmpty()) {
                topLevelPackageFqNames.add(FqName.ROOT)
            }
            for (fqName in allPackageFqNames) {
                var prefix = fqName
                while (!prefix.isRoot) {
                    val originalPrefix = prefix
                    prefix = prefix.parent()
                    fqNameByPrefix.putValue(prefix, fqName)
                    if (prefix.isRoot) {
                        topLevelPackageFqNames.add(originalPrefix)
                        break
                    }
                }
            }
        }

        /**
         * Known set of partial fqNames of scope.
         * Note: exact matching is a corner case of partial.
         *
         * Null when it is not possible to get and cache fqNames for the scope
         */
        private fun knownTopFqNames(scope: GlobalSearchScope): Collection<FqName>? {
            if (!enableSubpackageCaching) return null

            val moduleSourceScope = scope.safeAs<ModuleSourceScope>() ?: return null
            val module = moduleSourceScope.module
            val map: MutableMap<Class<out ModuleSourceScope>, Collection<FqName>> =
                knownTopFqNamesPerModule.computeIfAbsent(module) {
                    Collections.synchronizedMap(mutableMapOf<Class<out ModuleSourceScope>, Collection<FqName>>())
                }
            val scopeClass = moduleSourceScope.javaClass
            val result = map.computeIfAbsent(scopeClass) {
                val filterFqNames = KotlinPartialPackageNamesIndex.filterFqNames(topLevelPackageFqNames, scope)
                if (filterFqNames.size <= 3) filterFqNames.toList() else filterFqNames
            }
            // result has to have at least `<root>` element, if it is empty it means nothing is indexed in a scope, no reasons to cache it
            return if (result.isEmpty() && runReadAction { DumbService.isDumb(project) }) {
                map.remove(scopeClass)
                null
            } else {
                result
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

            return if (hasUnknownTopFqName(fqName, scope)) {
                false
            } else {
                PackageIndexUtil.containsFilesWithPartialPackage(fqName, scope)
            }
        }

        private fun hasUnknownTopFqName(fqName: FqName, scope: GlobalSearchScope): Boolean {
            val knownTopFqNames = knownTopFqNames(scope) ?: return false
            val topLevelFqName = KotlinPartialPackageNamesIndex.toTopLevelFqName(fqName)
            if (topLevelFqName !in knownTopFqNames) return true
            return false
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

            if (hasUnknownTopFqName(fqName, scope)) {
                return emptyList()
            }

            val subPackagesNames = hashSetOf<Name>()
            val len =  fqName.pathSegmentsSize()
            for (filesFqName in fqNames) {
                ProgressManager.checkCanceled()

                val subPackageName = filesFqName.pathSegments()[len]
                if (subPackageName in subPackagesNames || !nameFilter(subPackageName)) continue

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
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
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

class SubpackagesIndexService(private val project: Project): Disposable {

    private val enableSubpackageCaching = Registry.`is`("kotlin.cache.top.level.subpackages", false)

    private val cachedValue = CachedValuesManager.getManager(project).createCachedValue(
        {
            val dependencies = arrayOf(
                ProjectRootModificationTracker.getInstance(project),
                otherLanguagesModificationTracker(),
                KotlinPackageModificationListener.getInstance(project).packageTracker
            )
            CachedValueProvider.Result(
                SubpackagesIndex(KotlinExactPackagesIndex.getInstance().getAllKeys(project), dependencies),
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

    inner class SubpackagesIndex(allPackageFqNames: Collection<String>, val dependencies: Array<ModificationTracker>) {
        // a map from any existing package (in kotlin) to a set of subpackages (not necessarily direct) containing files
        private val allPackageFqNames = hashSetOf<FqName>()
        private val fqNameByPrefix = MultiMap.createSet<FqName, FqName>()
        private val ptCount = KotlinPackageModificationListener.getInstance(project).packageTracker.modificationCount

        init {
            for (fqNameAsString in allPackageFqNames) {
                val fqName = FqName(fqNameAsString)
                this.allPackageFqNames.add(fqName)

                var prefix = fqName
                while (!prefix.isRoot) {
                    prefix = prefix.parent()
                    fqNameByPrefix.putValue(prefix, fqName)
                }
            }
        }

        fun hasSubpackages(fqName: FqName, scope: GlobalSearchScope): Boolean {
            val cachedPartialFqNames: MutableMap<FqName, Boolean>? = cachedPartialFqNames(scope)
            cachedPartialFqNames?.get(fqName)?.takeIf { !it }?.let { return false }

            val fqNames = fqNameByPrefix[fqName]

            val knownNotContains = fqNames.isKnownNotContains(fqName, scope)
            if (knownNotContains) {
                return false
            }

            val any = fqNames.any { packageWithFilesFqName ->
                ProgressManager.checkCanceled()
                if (cachedPartialFqNames?.get(packageWithFilesFqName) == false) {
                    // there are production sources, test sources in module, therefore we can 100% rely only on a negative value
                    return@any false
                }
                val containsFilesWithExactPackage = PackageIndexUtil.containsFilesWithExactPackage(packageWithFilesFqName, scope, project)
                if (!containsFilesWithExactPackage) {
                    cachedPartialFqNames?.put(packageWithFilesFqName, containsFilesWithExactPackage)
                }
                containsFilesWithExactPackage
            }
            return any
        }

        private fun Collection<FqName>.isKnownNotContains(fqName: FqName, scope: GlobalSearchScope): Boolean {
            return enableSubpackageCaching && !fqName.isRoot && (isEmpty() ||
                    // fast check is reasonable when fqNames has more than 1 element
                    size > 1 && run {
                val partialFqName = KotlinPartialPackageNamesIndex.toPartialFqName(fqName)

                val cachedPartialFqNames: MutableMap<FqName, Boolean>? = cachedPartialFqNames(scope)
                cachedPartialFqNames?.get(partialFqName)?.let { return@run it }
                val notContains = !PackageIndexUtil.containsFilesWithPartialPackage(partialFqName, scope)
                cachedPartialFqNames?.put(partialFqName, notContains)
                notContains
            })
        }

        private fun cachedPartialFqNames(scope: GlobalSearchScope): MutableMap<FqName, Boolean>? {
            if (!enableSubpackageCaching) return null
            val module = scope.safeAs<ModuleSourceScope>()?.module ?: return null
            return module.cacheByProvider(*dependencies, provider = ::cachedPartialFqNamesProvider)
        }

        fun packageExists(fqName: FqName): Boolean = fqName in allPackageFqNames || fqNameByPrefix.containsKey(fqName)

        fun getSubpackages(fqName: FqName, scope: GlobalSearchScope, nameFilter: (Name) -> Boolean): Collection<FqName> {
            val cachedPartialFqNames: MutableMap<FqName, Boolean>? = cachedPartialFqNames(scope)
            cachedPartialFqNames?.get(fqName)?.takeIf { !it }?.let { return emptyList() }
            val fqNames = fqNameByPrefix[fqName]

            if (fqNames.isKnownNotContains(fqName, scope)) {
                return emptyList()
            }

            val existingSubPackagesShortNames = HashSet<Name>()
            val len = fqName.pathSegments().size
            for (filesFqName in fqNames) {
                ProgressManager.checkCanceled()
                val candidateSubPackageShortName = filesFqName.pathSegments()[len]
                if (candidateSubPackageShortName in existingSubPackagesShortNames || !nameFilter(candidateSubPackageShortName)) continue

                if (cachedPartialFqNames?.get(filesFqName) == false) {
                    continue
                }

                val existsInThisScope = PackageIndexUtil.containsFilesWithExactPackage(filesFqName, scope, project)
                if (existsInThisScope) {
                    existingSubPackagesShortNames.add(candidateSubPackageShortName)
                } else {
                    cachedPartialFqNames?.run { put(filesFqName, false) }
                }
            }

            return existingSubPackagesShortNames.map { fqName.child(it) }
        }

        override fun toString() = "SubpackagesIndex: PTC on creation $ptCount, all packages size ${allPackageFqNames.size}"
    }

    companion object {
        fun getInstance(project: Project): SubpackagesIndex {
            return project.getServiceSafe<SubpackagesIndexService>().cachedValue.value!!
        }
    }
}

// to avoid capture of extra field (esp. `dependencies`) into a provider lambda
private fun cachedPartialFqNamesProvider() = Collections.synchronizedMap(mutableMapOf<FqName, Boolean>())


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
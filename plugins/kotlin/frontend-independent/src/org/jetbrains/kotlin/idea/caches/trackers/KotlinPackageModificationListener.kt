// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.pom.PomManager
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent
import com.intellij.pom.event.PomModelListener
import com.intellij.pom.tree.TreeAspect
import com.intellij.pom.tree.events.TreeChangeEvent
import com.intellij.pom.tree.events.impl.ChangeInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.util.findTopmostParentOfType
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Tracks potential package changes
 * - In Kotlin and Java package directives
 * - VFS changes line created new file/folder (handled by [VfsCodeBlockModificationListener])
 * - Plugins load/unload
 *
 * Tested in [OutOfBlockModificationTestGenerated]
 */
class KotlinPackageModificationListener(project: Project): Disposable {
    private val trackerImpl = SimpleModificationTracker()

    val packageTracker: ModificationTracker = trackerImpl

    init {
        val messageBusConnection = project.messageBus.connect(this)

        messageBusConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                incModificationCount()
            }

            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                incModificationCount()
            }
        })

        val treeAspect: TreeAspect = TreeAspect.getInstance(project)
        val model = PomManager.getModel(project)

        model.addModelListener(
            object : PomModelListener {
                override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean = aspect == treeAspect

                private fun PsiElement.findPackageDirectiveFqName(): FqName? {
                    findTopmostParentOfType<KtPackageDirective>(false)?.let {
                        return it.fqName
                    }

                    return findTopmostParentOfType<PsiPackageStatement>(false)?.let {
                        FqName(it.packageName)
                    }
                }

                private inline fun ASTNode?.packageDirectiveChange() =
                    this is PsiPackageStatement || this?.elementType == KtStubElementTypes.PACKAGE_DIRECTIVE

                override fun modelChanged(event: PomModelEvent) {
                    val changeSet = event.getChangeSet(treeAspect).safeAs<TreeChangeEvent>() ?: return

                    // track only Kotlin and Java files
                    changeSet.rootElement.psi.containingFile.takeIf { it is KtFile || it is PsiJavaFile } ?: return

                    val packageChange = changeSet.changedElements.any { changedElement ->
                        if (changedElement.packageDirectiveChange()) return@any true

                        val changesByElement = changeSet.getChangesByElement(changedElement)
                        changesByElement.affectedChildren.any child@ { affectedChild ->
                            if (affectedChild.packageDirectiveChange()) return@child true
                            val oldChildNode = changesByElement.getChangeByChild(affectedChild).safeAs<ChangeInfoImpl>()?.oldChildNode
                            if (oldChildNode?.packageDirectiveChange() == true) return@child true

                            affectedChild.psi.findPackageDirectiveFqName()?.let { return@child true }
                            oldChildNode?.psi?.findPackageDirectiveFqName() != null
                        }
                    }
                    if (packageChange) {
                        incModificationCount()
                    }
                }
            }
        )
    }

    fun incModificationCount() {
        trackerImpl.incModificationCount()
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): KotlinPackageModificationListener = project.getServiceSafe()
    }
}
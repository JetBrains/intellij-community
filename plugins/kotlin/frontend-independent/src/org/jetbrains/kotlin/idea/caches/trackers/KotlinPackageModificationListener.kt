// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.pom.PomManager
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent
import com.intellij.pom.event.PomModelListener
import com.intellij.pom.tree.TreeAspect
import com.intellij.pom.tree.events.TreeChangeEvent
import com.intellij.pom.tree.events.impl.ChangeInfoImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findTopmostParentOfType
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.stubs.elements.KtFileElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Tracks potential package changes
 * - In Kotlin and Java package directives
 * - VFS changes line created new file/folder
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

        val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)
        val vfsEventsListener = AsyncVfsEventsListener { events ->
            val relatedVfsFileChange = events.any { event ->
                event.takeIf { it.isFromRefresh || it is VFileContentChangeEvent }?.file?.let {
                    it.isKotlinFileType() && fileIndex.isInContent(it)
                } ?: false
            }
            if (relatedVfsFileChange) {
                incModificationCount()
            }
        }

        AsyncVfsEventsPostProcessor.getInstance().addListener(vfsEventsListener, this)

        val treeAspect: TreeAspect = TreeAspect.getInstance(project)
        val model = PomManager.getModel(project)

        model.addModelListener(
            object : PomModelListener {
                override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean = aspect == treeAspect

                private fun PsiElement.findPackageDirectiveFqName(): FqName? {
                    return findTopmostParentOfType<KtPackageDirective>(false)?.let {
                        return it.fqName
                    }
                }

                private inline fun ASTNode?.packageDirectiveChange() =
                    this?.elementType == KtStubElementTypes.PACKAGE_DIRECTIVE

                override fun modelChanged(event: PomModelEvent) {
                    val changeSet = event.getChangeSet(treeAspect).safeAs<TreeChangeEvent>() ?: return

                    // track only Kotlin
                    changeSet.rootElement.psi.containingFile.takeIf { it is KtFile } ?: return

                    val packageChange = changeSet.changedElements.any { changedElement ->
                        if (changedElement.packageDirectiveChange()) return@any true

                        val changesByElement = changeSet.getChangesByElement(changedElement)
                        changesByElement.affectedChildren.any child@ { affectedChild ->
                            if (affectedChild.packageDirectiveChange()) return@child true
                            val oldChildNode = changesByElement.getChangeByChild(affectedChild).safeAs<ChangeInfoImpl>()?.oldChildNode
                            // if oldChildNode is null and new child is Kotlin file - it means file was not exist
                            if (oldChildNode == null && changedElement.elementType == KtFileElementType.INSTANCE ||
                                oldChildNode?.packageDirectiveChange() == true
                            ) return@child true

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

    private fun incModificationCount() {
        trackerImpl.incModificationCount()
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): KotlinPackageModificationListener = project.getServiceSafe()
    }
}
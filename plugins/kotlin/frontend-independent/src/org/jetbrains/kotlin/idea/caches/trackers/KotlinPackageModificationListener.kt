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
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.util.findTopmostParentOfType
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Tracks potential package changes
 * - In Kotlin and Java package directives
 * - VFS changes line created new file/folder (handled by [VfsCodeBlockModificationListener])
 * - Plugins load/unload
 *
 * Tested in [OutOfBlockModificationTestGenerated]
 */
class KotlinPackageModificationListener(private val project: Project): Disposable {
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

                override fun modelChanged(event: PomModelEvent) {
                    val changeSet = event.getChangeSet(treeAspect) as TreeChangeEvent? ?: return

                    val packageChange = isAnyInChange(changeSet) {
                        val psi = it?.psi
                        val packageDirective = psi.safeAs<KtPackageDirective>() ?: psi?.findTopmostParentOfType<KtPackageDirective>()

                        if (packageDirective != null) {
                            // ignore implicit `package <root>`
                            return@isAnyInChange !packageDirective.fqName.isRoot
                        }
                        val psiPackageStatement = psi.safeAs<PsiPackageStatement>() ?: psi?.findTopmostParentOfType<PsiPackageStatement>() ?: return@isAnyInChange false
                        // ignore implicit `package <root>`
                        psiPackageStatement.packageName.isNotEmpty()
                    }
                    if (packageChange) {
                        incModificationCount()
                    }
                }
            }
        )
    }

    private fun isAnyInChange(changeSet: TreeChangeEvent, precondition: (ASTNode?) -> Boolean): Boolean =
        changeSet.changedElements.any { changedElement ->
            val changesByElement = changeSet.getChangesByElement(changedElement)
            changesByElement.affectedChildren.any { affectedChild ->
                precondition(affectedChild) || changesByElement.getChangeByChild(affectedChild).let { changeByChild ->
                    if (changeByChild is ChangeInfoImpl) {
                        val oldChild = changeByChild.oldChildNode
                        precondition(oldChild)
                    } else false
                }
            }
        }

    fun incModificationCount() {
        trackerImpl.incModificationCount()
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): KotlinPackageModificationListener = project.getServiceSafe()
    }
}
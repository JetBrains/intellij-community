// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.psi.*

val KOTLIN_CONSOLE_KEY = Key.create<Boolean>("kotlin.console")

/**
 * Tested in [OutOfBlockModificationTestGenerated]
 */
class KotlinCodeBlockModificationListener(project: Project) : PsiTreeChangePreprocessor, Disposable {
    private val modificationTrackerImpl: PsiModificationTracker =
        PsiModificationTracker.SERVICE.getInstance(project)

    @Volatile
    private var kotlinModificationCount: Long = 0

    private val kotlinOutOfCodeBlockTrackerImpl = SimpleModificationTracker()

    val kotlinOutOfCodeBlockTracker: ModificationTracker = kotlinOutOfCodeBlockTrackerImpl

    private val pureKotlinCodeBlockModificationListener: PureKotlinCodeBlockModificationListener = project.getServiceSafe()

    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (!PsiModificationTrackerImpl.canAffectPsi(event)) {
            return
        }

        // Copy logic from PsiModificationTrackerImpl.treeChanged(). Some out-of-code-block events are written to language modification
        // tracker in PsiModificationTrackerImpl but don't have correspondent PomModelEvent. Increase kotlinOutOfCodeBlockTracker
        // manually if needed.
        val outOfCodeBlock = when (event.code) {
            PROPERTY_CHANGED ->
                event.propertyName === PsiTreeChangeEvent.PROP_UNLOADED_PSI || event.propertyName === PsiTreeChangeEvent.PROP_ROOTS
            CHILD_MOVED -> event.oldParent is PsiDirectory || event.newParent is PsiDirectory
            else -> event.parent is PsiDirectory
        }

        if (outOfCodeBlock) {
            incModificationCount()
        }
    }

    companion object {
        fun getInstance(project: Project): KotlinCodeBlockModificationListener = project.getServiceSafe()
    }

    init {
        val messageBusConnection = project.messageBus.connect(this)

        val perModuleOutOfCodeBlockTrackerUpdater = KotlinModuleOutOfCodeBlockModificationTracker.getUpdaterInstance(project)
        pureKotlinCodeBlockModificationListener.addListener(
            object : PureKotlinOutOfCodeBlockModificationListener {
                override fun kotlinFileOutOfCodeBlockChanged(file: KtFile, physical: Boolean) {
                    messageBusConnection.deliverImmediately()

                    if (physical) {
                        incModificationCount()
                        perModuleOutOfCodeBlockTrackerUpdater.onKotlinPhysicalFileOutOfBlockChange(file, true)
                    }
                }
            },
            this
        )

        (PsiManager.getInstance(project) as PsiManagerImpl).addTreeChangePreprocessor(this)

        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
            val kotlinTrackerInternalIDECount = modificationTrackerImpl.forLanguage(KotlinLanguage.INSTANCE).modificationCount
            if (kotlinModificationCount == kotlinTrackerInternalIDECount) {
                // Some update that we are not sure is from Kotlin language, as Kotlin language tracker wasn't changed
                incModificationCount()
            } else {
                kotlinModificationCount = kotlinTrackerInternalIDECount
            }

            perModuleOutOfCodeBlockTrackerUpdater.onPsiModificationTrackerUpdate()
        })

        messageBusConnection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                incModificationCount()
            }

            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                incModificationCount()
            }
        })
    }

    fun incModificationCount() {
        kotlinOutOfCodeBlockTrackerImpl.incModificationCount()
    }

    override fun dispose() = Unit
}

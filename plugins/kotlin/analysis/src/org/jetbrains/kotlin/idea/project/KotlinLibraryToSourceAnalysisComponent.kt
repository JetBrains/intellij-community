// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import java.util.concurrent.atomic.AtomicBoolean

object KotlinLibraryToSourceAnalysisComponent {
    @JvmStatic
    fun setState(project: Project, isEnabled: Boolean) {
        KotlinLibraryToSourceAnalysisStateComponent.getInstance(project).setEnabled(isEnabled)
    }

    @JvmStatic
    fun isEnabled(project: Project): Boolean =
        KotlinLibraryToSourceAnalysisStateComponent.getInstance(project).isEnabled.get()
}

@State(name = "LibraryToSourceAnalysisState", storages = [Storage("anchors.xml")])
class KotlinLibraryToSourceAnalysisStateComponent : PersistentStateComponent<KotlinLibraryToSourceAnalysisStateComponent> {
    @JvmField
    @OptionTag(converter = AtomicBooleanXmlbConverter::class)
    var isEnabled: AtomicBoolean = AtomicBoolean(false)

    private var shouldPersist: Boolean = true

    override fun getState(): KotlinLibraryToSourceAnalysisStateComponent? =
        if (shouldPersist) this else null

    override fun loadState(state: KotlinLibraryToSourceAnalysisStateComponent) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun noStateLoaded() {
        shouldPersist = false
    }

    fun setEnabled(newState: Boolean) {
        isEnabled.getAndSet(newState)
    }

    override fun equals(other: Any?): Boolean {
        return other is KotlinLibraryToSourceAnalysisStateComponent && other.isEnabled.get() == isEnabled.get()
    }

    override fun hashCode(): Int {
        return isEnabled.get().hashCode()
    }

    companion object {
        fun getInstance(project: Project): KotlinLibraryToSourceAnalysisStateComponent = project.getServiceSafe()
    }
}

class AtomicBooleanXmlbConverter : Converter<AtomicBoolean>() {
    override fun toString(value: AtomicBoolean): String = if (value.get()) TRUE else FALSE

    override fun fromString(value: String): AtomicBoolean? = when (value) {
        TRUE -> AtomicBoolean(true)
        FALSE -> AtomicBoolean(false)
        else -> null
    }

    companion object {
        private const val TRUE = "true"
        private const val FALSE = "false"
    }
}

val Project.libraryToSourceAnalysisEnabled: Boolean
    get() = KotlinLibraryToSourceAnalysisComponent.isEnabled(this)

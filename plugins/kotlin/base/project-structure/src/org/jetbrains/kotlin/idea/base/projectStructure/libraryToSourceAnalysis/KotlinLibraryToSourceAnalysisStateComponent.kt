// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinLibraryToSourceAnalysisStateComponentUtils")
package org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var Project.useLibraryToSourceAnalysis: Boolean
    get() = KotlinLibraryToSourceAnalysisStateComponent.getInstance(this).isEnabled.get()
    internal set(newValue) = KotlinLibraryToSourceAnalysisStateComponent.getInstance(this).setEnabled(newValue)

@TestOnly
@ApiStatus.Internal
fun Project.withLibraryToSourceAnalysis(block: () -> Unit) {
    val oldValue = useLibraryToSourceAnalysis
    try {
        useLibraryToSourceAnalysis = true
        block()
    } finally {
        useLibraryToSourceAnalysis = oldValue
    }
}

@State(name = "LibraryToSourceAnalysisState", storages = [Storage("anchors.xml")])
internal class KotlinLibraryToSourceAnalysisStateComponent : PersistentStateComponent<KotlinLibraryToSourceAnalysisStateComponent> {
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
        fun getInstance(project: Project): KotlinLibraryToSourceAnalysisStateComponent = project.service()
    }
}

private class AtomicBooleanXmlbConverter : Converter<AtomicBoolean>() {
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
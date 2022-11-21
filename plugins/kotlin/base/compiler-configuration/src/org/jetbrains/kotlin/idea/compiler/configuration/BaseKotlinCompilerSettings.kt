// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilterBase
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.kotlin.cli.common.arguments.*
import kotlin.reflect.KClass

abstract class BaseKotlinCompilerSettings<T : Freezable> protected constructor(private val project: Project) :
    PersistentStateComponent<Element>, Cloneable {
    // Based on com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
    private object DefaultValuesFilter : SerializationFilterBase() {
        private val defaultBeans = HashMap<Class<*>, Any>()

        private fun createDefaultBean(beanClass: Class<Any>): Any {
            return ReflectionUtil.newInstance(beanClass).apply {
                if (this is K2JSCompilerArguments) {
                    sourceMapPrefix = ""
                }
            }
        }

        private fun getDefaultValue(accessor: Accessor, bean: Any): Any? {
            if (bean is K2JSCompilerArguments && accessor.name == K2JSCompilerArguments::sourceMapEmbedSources.name) {
                return if (bean.sourceMap) K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING else null
            }

            val beanClass = bean.javaClass
            val defaultBean = defaultBeans.getOrPut(beanClass) { createDefaultBean(beanClass) }
            return accessor.read(defaultBean)
        }

        override fun accepts(accessor: Accessor, bean: Any, beanValue: Any?): Boolean {
            val defValue = getDefaultValue(accessor, bean)
            return if (defValue is Element && beanValue is Element) {
                !JDOMUtil.areElementsEqual(beanValue, defValue)
            } else {
                !Comparing.equal(beanValue, defValue)
            }
        }
    }

    @Suppress("LeakingThis")
    private var _settings: T = createSettings().frozen()
        private set(value) {
            field = value.frozen()
        }

    var settings: T
        get() = _settings
        set(value) {
            val oldSettings = _settings
            validateNewSettings(value)
            _settings = value

            runInEdt {
                runWriteAction {
                    KotlinCompilerSettingsTracker.getInstance(project).incModificationCount()

                    project.messageBus.syncPublisher(KotlinCompilerSettingsListener.TOPIC).settingsChanged(
                        oldSettings = oldSettings,
                        newSettings = _settings,
                    )
                }
            }
        }

    fun update(changer: T.() -> Unit) {
        settings = settings.unfrozen().apply { changer() }
    }

    protected fun validateInheritedFieldsUnchanged(settings: T) {
        @Suppress("UNCHECKED_CAST")
        val inheritedProperties = collectProperties(settings::class as KClass<T>, true)
        val defaultInstance = createSettings()
        val invalidFields = inheritedProperties.filter { it.get(settings) != it.get(defaultInstance) }
        if (invalidFields.isNotEmpty()) {
            throw IllegalArgumentException("Following fields are expected to be left unchanged in ${settings.javaClass}: ${invalidFields.joinToString { it.name }}")
        }
    }

    protected open fun validateNewSettings(settings: T) {}

    protected abstract fun createSettings(): T

    override fun getState() = XmlSerializer.serialize(_settings, DefaultValuesFilter)

    override fun loadState(state: Element) {
        _settings = ReflectionUtil.newInstance(_settings.javaClass).apply {
            if (this is CommonCompilerArguments) {
                freeArgs = mutableListOf()
                internalArguments = mutableListOf()
            }
            XmlSerializer.deserializeInto(this, state)
        }

        runInEdt {
            runWriteAction {
                KotlinCompilerSettingsTracker.getInstance(project).incModificationCount()

                project.messageBus.syncPublisher(KotlinCompilerSettingsListener.TOPIC).settingsChanged(
                    oldSettings = null,
                    newSettings = settings,
                )
            }
        }
    }

    public override fun clone(): Any = super.clone()
}

interface KotlinCompilerSettingsListener {
    fun <T> settingsChanged(oldSettings: T?, newSettings: T?)

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic(KotlinCompilerSettingsListener::class.java, BroadcastDirection.TO_CHILDREN, true)
    }
}

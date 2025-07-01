// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization.lookup

import com.intellij.codeInsight.completion.BaseCompletionService.LOOKUP_ELEMENT_CONTRIBUTOR
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model.LookupElementModel
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model.LookupObjectModel
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model.PsiElementModel
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model.UserDataValueModel
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.idea.completion.api.serialization.ensureSerializable
/**
 * Converts a [LookupElement] to a [LookupElementModel] for serialization for Kotlin LSP
 *
 * In Kotlin LSP, to compute text edits which we need to return to the client for each lookup element,
 * we need to be able to pass some crucial parts of [LookupElement] between requests.
 * Specifically, the parts which are used by the [InsertHandler] and the [InsertHandler] itself.
 * That means that those parts should be convertible to JSON.
 *
 * For this, the [LookupElementModel] is introduced which is serialized via kotlinx.serialization.
 *
 * It brings some restrictions on the [LookupElement]s:
 * - [LookupElement.getObject]s should always be [SerializableLookupObject] and to be registered in [org.jetbrains.kotlin.idea.completion.impl.k2.serializableInsertionHandlerSerializersModule]
 * - All used [InsertHandler]s should be serializable, should not capture any local state and should be registered in [org.jetbrains.kotlin.idea.completion.impl.k2.serializableInsertionHandlerSerializersModule]
 * - All user data should be serializable, see [UserDataValueModel] for supported types
 */
@ApiStatus.Internal
object LookupModelConverter {

    data class Config(
        /**
         * If true, then exceptions thrown during conversion will be logged instead of rethrown
         */
        val safeMode: Boolean,
    )

    fun serializeLookupElementForInsertion(lookupElement: LookupElement, config: Config): LookupElementModel? {
        if (lookupElement.isIgnored()) return null
        return when (lookupElement) {
            is CommandCompletionLookupElement -> null
            is LookupElementDecorator<*> -> {
                LookupElementModel.LookupElementDecoratorModel(
                    serializeLookupElementForInsertion(lookupElement.delegate, config) ?: return null,
                    lookupElement.decoratorInsertHandler?.ensureSerializable(),
                    lookupElement.delegateInsertHandler?.ensureSerializable(),
                )
            }

            is LookupElementBuilder -> {
                return LookupElementModel.LookupElementBuilderModel(
                    lookupElement.lookupString,
                    when (val lookupObject = lookupElement.`object`) {
                        is SerializableLookupObject -> LookupObjectModel.SerializableLookupObjectModel(lookupObject)
                        is PsiElement -> LookupObjectModel.PsiLookupObjectModel(PsiElementModel.create(lookupObject))

                        is String -> LookupObjectModel.StringLookupObjectModel(lookupObject)
                        else -> throw AssertionError("Unexpected lookup object type: ${lookupObject::class.java}")
                    },
                    lookupElement.psiElement?.let { PsiElementModel.create(it) },
                    lookupElement.insertHandler?.ensureSerializable(),
                    serializeUserData(lookupElement, config),
                )
            }

            is PostfixTemplateLookupElement -> {
                null
            }

            else -> {
                AssertionError("Unexpected lookup element type: ${lookupElement::class.java}").throwOrLog(config)
                null
            }
        }
    }

    fun deserializeLookupElementForInsertion(model: LookupElementModel, project: Project): LookupElement {
        return when (model) {
            is LookupElementModel.LookupElementDecoratorModel -> {
                LookupElementDecoratorWithDelegateInsertHandler(
                    deserializeLookupElementForInsertion(model.delegate, project),
                    model.delegateInsertHandler
                )
            }

            is LookupElementModel.LookupElementBuilderModel -> {
                LookupElementBuilder.create(
                    /* lookupObject = */ when (val lookupObject = model.lookupObject) {
                        is LookupObjectModel.SerializableLookupObjectModel -> lookupObject.lookupObject
                        is LookupObjectModel.StringLookupObjectModel -> lookupObject.string
                        is LookupObjectModel.PsiLookupObjectModel -> lookupObject.psiElement.restore(project)
                            ?: AssertionError("Cannot restore psi element for ${model.lookupElementString}, element is ${model.psiElement?.elementClass}")
                    },
                    /* lookupString = */ model.lookupElementString
                )
                    .withPsiElement(model.psiElement?.restore(project))
                    .withInsertHandler(model.insertHandler)
                    .apply {
                        val userData = deserializeUserData(model.userdata, project)
                        userData.copyUserDataTo(this)
                    }
            }
        }
    }

    private fun serializeUserData(data: UserDataHolderBase, config: Config): Map<String, UserDataValueModel> {
        val map = data.get()
        if (map.isEmpty()) return emptyMap()
        return buildMap {
            for (key in map.keys) {
                val keyName = key.toString()
                if (keyName in keysToIgnore) {
                    continue
                }
                val value = map[key]!!
                put(
                    keyName,
                    when (value) {
                        is Boolean -> UserDataValueModel.BooleanModel(value)
                        is String -> UserDataValueModel.StringModel(value)
                        is Enum<*> -> UserDataValueModel.EnumModel(value.ordinal, value::class.java.name)
                        is Class<*> -> UserDataValueModel.ClassModel(value.name)
                        is Int -> UserDataValueModel.IntModel(value)
                        is Long -> UserDataValueModel.LongModel(value)
                        is Name -> UserDataValueModel.NameModel(value)
                        is PsiReference -> UserDataValueModel.PsiReferenceModel.create(value)
                        else -> {
                            AssertionError("Unexpected user data value type: `${value::class.java}` for key `$key`").throwOrLog(config)
                            continue
                        }
                    }
                )
            }
        }
    }

    private val keysToIgnore = setOf(
        // weighters are not needed for insertion
        "KOTLIN_CLASSIFIER_WEIGHT", "KOTLIN_CALLABlE_WEIGHT",
        // not needed for insertion
        "LookupArrangerMatcher",
        "LAST_COMPUTED_PRESENTATION",
        "SORTER_KEY",
        "Base statistics info",
        LOOKUP_ELEMENT_CONTRIBUTOR.toString(),
        "PRESENTATION_INVARIANT",
        // TODO, support it
        "SHORTEN_COMMAND",
    )

    private fun deserializeUserData(data: Map<String, UserDataValueModel>, project: Project): UserDataHolderBase {
        val result = UserDataHolderBase()
        for ((k, v) in data) {
            val key = Key.findKeyByName(k) as Key<Any>
            val value = when (v) {
                is UserDataValueModel.BooleanModel -> v.value
                is UserDataValueModel.StringModel -> v.value
                is UserDataValueModel.EnumModel -> Class.forName(v.enumClass).enumConstants[v.ordinal]
                is UserDataValueModel.ClassModel -> Class.forName(v.className)
                is UserDataValueModel.IntModel -> v.value
                is UserDataValueModel.LongModel -> v.value
                is UserDataValueModel.NameModel -> v.name
                is UserDataValueModel.PsiReferenceModel -> v.restore(project)
            }
            result.putUserData(key, value)
        }
        return result
    }

    private fun LookupElement.isIgnored(): Boolean {
        return this::class.java in ignoredLookupElementClasses
    }

    // todo move to the class declaration site
    private val ignoredLookupElementClasses: Set<Class<*>> by lazy {
        listOf(
            "org.jetbrains.kotlin.idea.completion.OverridesCompletionLookupElementDecorator", // TODO support later
        ).mapTo(mutableSetOf()) {
            Class.forName(it)
        }
    }

    private fun Throwable.throwOrLog(config: Config) {
        if (config.safeMode) {
            LOG.error(this)
        } else {
            throw this
        }
    }

    private val LOG = logger<LookupModelConverter>()
}

private class LookupElementDecoratorWithDelegateInsertHandler(
    delegate: LookupElement,
    private val delegateInsertHandler: InsertHandler<LookupElement>?,
) : LookupElementDecorator<LookupElement>(delegate) {
    override fun getDelegateInsertHandler(): InsertHandler<LookupElement>? {
        return this.delegateInsertHandler
    }
}
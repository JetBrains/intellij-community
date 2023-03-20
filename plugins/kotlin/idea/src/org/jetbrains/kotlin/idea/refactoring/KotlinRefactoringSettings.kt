// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @see KotlinCommonRefactoringSettings
 */
@State(name = "KotlinRefactoringSettings", storages = [Storage("kotlinRefactoring.xml")], category = SettingsCategory.CODE)
class KotlinRefactoringSettings : PersistentStateComponent<KotlinRefactoringSettings> {
    @JvmField
    var MOVE_TO_UPPER_LEVEL_SEARCH_IN_COMMENTS = false

    @JvmField
    var MOVE_TO_UPPER_LEVEL_SEARCH_FOR_TEXT = false

    @JvmField
    var RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = false

    @JvmField
    var RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = false

    @JvmField
    var RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false

    @JvmField
    var RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = false

    @JvmField
    var RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = false

    @JvmField
    var RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = false

    @JvmField
    var RENAME_SEARCH_FOR_TEXT_FOR_CLASS = false

    @JvmField
    var RENAME_SEARCH_FOR_TEXT_FOR_METHOD = false

    @JvmField
    var RENAME_SEARCH_FOR_TEXT_FOR_FIELD = false

    @JvmField
    var RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = false

    @JvmField
    var MOVE_PREVIEW_USAGES = true

    @JvmField
    var MOVE_SEARCH_IN_COMMENTS = true

    @JvmField
    var MOVE_SEARCH_FOR_TEXT = true

    @JvmField
    var MOVE_DELETE_EMPTY_SOURCE_FILES = true

    @JvmField
    var MOVE_MPP_DECLARATIONS = true

    @JvmField
    var EXTRACT_INTERFACE_JAVADOC: Int = 0

    @JvmField
    var EXTRACT_SUPERCLASS_JAVADOC: Int = 0

    @JvmField
    var PULL_UP_MEMBERS_JAVADOC: Int = 0

    @JvmField
    var PUSH_DOWN_PREVIEW_USAGES: Boolean = false

    var INLINE_METHOD_THIS: Boolean = false
    var INLINE_LOCAL_THIS: Boolean = false
    var INLINE_TYPE_ALIAS_THIS: Boolean = false
    var INLINE_METHOD_KEEP: Boolean = false
    var INLINE_PROPERTY_KEEP: Boolean = false
    var INLINE_TYPE_ALIAS_KEEP: Boolean = false

    var renameInheritors = true
    var renameParameterInHierarchy = true
    var renameVariables = true
    var renameTests = true
    var renameOverloads = true

    override fun getState() = this

    override fun loadState(state: KotlinRefactoringSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        @JvmStatic
        val instance: KotlinRefactoringSettings
            get() = service()
    }
}
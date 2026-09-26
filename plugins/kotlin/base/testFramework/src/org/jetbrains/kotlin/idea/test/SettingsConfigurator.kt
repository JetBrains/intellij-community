// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class SettingsConfigurator(fileText: String, private vararg val objects: Any?) {
    companion object {
        const val SET_TRUE_DIRECTIVE = "SET_TRUE:"
        const val SET_FALSE_DIRECTIVE = "SET_FALSE:"
        private const val SET_INT_DIRECTIVE = "SET_INT:"
    }

    private val settingsToTrue: Array<String> = InTextDirectivesUtils.findArrayWithPrefixes(fileText, SET_TRUE_DIRECTIVE)
    private val settingsToFalse: Array<String> = InTextDirectivesUtils.findArrayWithPrefixes(fileText, SET_FALSE_DIRECTIVE)
    private val settingsToIntValue: List<Pair<String, Int>> = InTextDirectivesUtils.findArrayWithPrefixes(fileText, SET_INT_DIRECTIVE).map { s ->
        val tokens = s.split("=")
        Pair(tokens[0].trim(), tokens[1].trim().toInt())
    }

    private fun setSettingValue(settingName: String, value: Any, valueType: Class<*>, vararg objects: Any?) {
        for (obj in objects) {
            if (setSettingWithField(settingName, obj, value) || setSettingWithMethod(settingName, obj, value, valueType)) {
                return
            }
        }

        throw IllegalArgumentException(
            "There's no property or method with name '$settingName' in given objects: ${objects.contentToString()}"
        )
    }

    private fun setBooleanSetting(setting: String, value: Boolean, vararg objects: Any?) {
        setSettingValue(setting, value, Boolean::class.javaPrimitiveType!!, *objects)
    }

    private fun setIntSetting(setting: String, value: Int, vararg objects: Any?) {
        setSettingValue(setting, value, Int::class.javaPrimitiveType!!, *objects)
    }

    fun configureSettings() {
        for (trueSetting in settingsToTrue) {
            setBooleanSetting(trueSetting, true, *objects)
        }

        for (falseSetting in settingsToFalse) {
            setBooleanSetting(falseSetting, false, *objects)
        }

        for (setting in settingsToIntValue) {
            setIntSetting(setting.first, setting.second, *objects)
        }
    }

    fun configureInvertedSettings() {
        for (trueSetting in settingsToTrue) {
            setBooleanSetting(trueSetting, false, *objects)
        }

        for (falseSetting in settingsToFalse) {
            setBooleanSetting(falseSetting, true, *objects)
        }

        for (setting in settingsToIntValue) {
            setIntSetting(setting.first, setting.second, *objects)
        }
    }

    private fun setSettingWithField(settingName: String, obj: Any?, value: Any): Boolean {
        if (obj == null) return false

        try {
            val field: Field = obj.javaClass.getField(settingName)
            field.set(obj, value)
            return true
        }
        catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Can't set property with the name $settingName in object $obj")
        }
        catch (e: NoSuchFieldException) {
            // Do nothing - will try other variants
        }

        return false
    }

    private fun setSettingWithMethod(setterName: String, obj: Any?, value: Any, valueType: Class<*>): Boolean {
        if (obj == null) return false

        try {
            val method: Method = obj.javaClass.getMethod(setterName, valueType)
            method.invoke(obj, value)
            return true
        }
        catch (e: InvocationTargetException) {
            throw IllegalArgumentException("Can't call method with name $setterName for object $obj")
        }
        catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Can't access to method with name $setterName for object $obj")
        }
        catch (e: NoSuchMethodException) {
            // Do nothing - will try other variants
        }

        return false
    }
}

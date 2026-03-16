package org.jetbrains.kotlin.gradle.scripting.shared

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.util.AbstractKotlinBundle

@NonNls
private const val BUNDLE = "messages.KotlinGradleScriptingBundle"

object KotlinGradleScriptingBundle : AbstractKotlinBundle(BUNDLE) {
    @Nls
    @JvmStatic
    fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }

    @Nls
    @JvmStatic
    fun htmlMessage(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params).withHtml()
    }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import org.hamcrest.*
import org.jetbrains.kotlin.idea.script.TransformingMatcher.Companion.transformInput

internal fun Collection<Any>.assertContainsOnly(reason: String, vararg elementPatterns: Map<String, Any>) {
    val elementMatchers = elementPatterns.map { buildListElementMatcher(it) }
    MatcherAssert.assertThat(reason, this, Matchers.containsInAnyOrder(elementMatchers))
}

private fun buildListElementMatcher(elemPropertiesPatterns: Map<String, Any>): Matcher<Any> {
    if (elemPropertiesPatterns.size == 1) {
        val (propertyName, value) = elemPropertiesPatterns.entries.single()
        return buildElementPropertyMatcher(propertyName, value)
    }

    return Matchers.allOf(
        elemPropertiesPatterns.map { (propertyName, value) ->
            buildElementPropertyMatcher(propertyName, value)
        })
}

@Suppress("UNCHECKED_CAST")
private fun buildElementPropertyMatcher(propertyName: String, value: Any): Matcher<Any> = when (value) {
    is String -> Matchers.hasProperty(propertyName, Matchers.`is`(value))
    is Pair<*, *> -> {
        val transformer = value.first as (Any) -> String
        Matchers.hasProperty(propertyName, transformInput(Matchers.`is`(value.second), transformer))
    }
    is List<*> -> {
        val elementPatterns = value as List<Map<String, Any>>
        if (elementPatterns.isEmpty()) {
            Matchers.hasProperty(propertyName, Matchers.empty<Map<String, Any>>())
        } else {
            val elementMatchers = elementPatterns.map { buildListElementMatcher(it) }
            Matchers.hasProperty(propertyName, Matchers.containsInAnyOrder(elementMatchers))
        }
    }
    else -> error("unexpected")
}


internal class TransformingMatcher<U, T>(base: Matcher<T>, function: (U) -> T) : TypeSafeMatcher<U>() {
    companion object {
        fun <U, T> transformInput(base: Matcher<T>, function: (U) -> T): TransformingMatcher<U, T> {
            return TransformingMatcher(base, function)
        }
    }

    private val base: Matcher<T>
    private val function: (U) -> T

    init {
        this.base = base
        this.function = function
    }

    override fun describeTo(description: Description) {
        description.appendText("transformed version of ")
        base.describeTo(description)
    }

    override fun matchesSafely(item: U): Boolean {
        return base.matches(function(item))
    }
}
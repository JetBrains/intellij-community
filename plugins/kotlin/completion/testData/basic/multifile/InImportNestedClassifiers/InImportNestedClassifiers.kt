// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import second.One.<caret>

// EXIST: {"lookupString":"Companion","tailText":" (second.One)","icon":"org/jetbrains/kotlin/idea/icons/objectKotlin.svg","attributes":"","allLookupStrings":"Companion","itemText":"Companion"}
// EXIST: {"lookupString":"NestedClass","tailText":" (second.One)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"NestedClass","itemText":"NestedClass"}
// EXIST: {"lookupString":"NestedEnumClass","tailText":" (second.One)","icon":"org/jetbrains/kotlin/idea/icons/enumKotlin.svg","attributes":"","allLookupStrings":"NestedEnumClass","itemText":"NestedEnumClass"}
// EXIST: {"lookupString":"NestedObject","tailText":" (second.One)","icon":"org/jetbrains/kotlin/idea/icons/objectKotlin.svg","attributes":"","allLookupStrings":"NestedObject","itemText":"NestedObject"}
// NOTHING_ELSE
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import second.<caret>

// EXIST: {"lookupString":"Four","tailText":" (second)","icon":"org/jetbrains/kotlin/idea/icons/enumKotlin.svg","attributes":"","allLookupStrings":"Four","itemText":"Four"}
// EXIST: {"lookupString":"One","tailText":" (second)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"One","itemText":"One"}
// EXIST: {"lookupString":"Three","tailText":" (second)","icon":"org/jetbrains/kotlin/idea/icons/objectKotlin.svg","attributes":"","allLookupStrings":"Three","itemText":"Three"}
// EXIST: {"lookupString":"Two","tailText":" (second)","icon":"org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg","attributes":"","allLookupStrings":"Two","itemText":"Two"}
// EXIST: {"lookupString":"MyTypeAlias","tailText":" (second)","typeText":"One","icon":"org/jetbrains/kotlin/idea/icons/typeAlias.svg","attributes":"","allLookupStrings":"MyTypeAlias","itemText":"MyTypeAlias"}
// NOTHING_ELSE
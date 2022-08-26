// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlin.text.capitalize as xxx

/**
 * [String.x<caret>]
 */
fun foo(){}

// EXIST: { lookupString: "xxx", itemText: "xxx", icon: "Function"}

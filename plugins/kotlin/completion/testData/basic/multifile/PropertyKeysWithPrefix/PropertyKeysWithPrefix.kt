// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.annotations.PropertyKey

fun message(@PropertyKey(resourceBundle = "PropertyKeysWithPrefix") key: String) = key

fun test() {
    message("foo.<caret>")
}

// EXIST: { lookupString: "foo.bar", itemText: "foo.bar", tailText: "=1", typeText: "PropertyKeysWithPrefix", icon: "Property"}
// EXIST: { lookupString: "foo.bar.baz", itemText: "foo.bar.baz", tailText: "=3", typeText: "PropertyKeysWithPrefix", icon: "Property"}
// EXIST: { lookupString: "foo.test", itemText: "foo.test", tailText: "=4", typeText: "PropertyKeysWithPrefix", icon: "Property"}
// NOTHING_ELSE
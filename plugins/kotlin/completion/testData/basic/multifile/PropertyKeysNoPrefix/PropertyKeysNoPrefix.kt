// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.annotations.PropertyKey

fun message(@PropertyKey(resourceBundle = "PropertyKeysNoPrefix") key: String) = key

fun test() {
    message("<caret>foo")
}

// EXIST: { lookupString: "foo.bar", itemText: "foo.bar", tailText: "=1", typeText: "PropertyKeysNoPrefix", icon: "Property"}
// EXIST: { lookupString: "bar.baz", itemText: "bar.baz", tailText: "=2", typeText: "PropertyKeysNoPrefix", icon: "Property"}
// EXIST: { lookupString: "foo.bar.baz", itemText: "foo.bar.baz", tailText: "=3", typeText: "PropertyKeysNoPrefix", icon: "Property"}
// EXIST: { lookupString: "foo.test", itemText: "foo.test", tailText: "=4", typeText: "PropertyKeysNoPrefix", icon: "Property"}
// NOTHING_ELSE
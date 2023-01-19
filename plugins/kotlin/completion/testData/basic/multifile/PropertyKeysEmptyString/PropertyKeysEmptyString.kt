// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.annotations.PropertyKey

fun message(@PropertyKey(resourceBundle = "PropertyKeysEmptyString") key: String) = key

fun test() {
    message("<caret>")
}

// EXIST: { lookupString: "foo.bar", itemText: "foo.bar", tailText: "=1", typeText: "PropertyKeysEmptyString", icon: "Property"}
// EXIST: { lookupString: "bar.baz", itemText: "bar.baz", tailText: "=2", typeText: "PropertyKeysEmptyString", icon: "Property"}
// EXIST: { lookupString: "foo.bar.baz", itemText: "foo.bar.baz", tailText: "=3", typeText: "PropertyKeysEmptyString", icon: "Property"}
// EXIST: { lookupString: "foo.test", itemText: "foo.test", tailText: "=4", typeText: "PropertyKeysEmptyString", icon: "Property"}
// NOTHING_ELSE
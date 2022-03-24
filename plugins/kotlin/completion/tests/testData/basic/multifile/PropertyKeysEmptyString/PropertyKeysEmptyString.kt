import org.jetbrains.annotations.PropertyKey

fun message(@PropertyKey(resourceBundle = "PropertyKeysEmptyString") key: String) = key

fun test() {
    message("<caret>")
}

// EXIST: { lookupString: "foo.bar", itemText: "foo.bar", tailText: "=1", typeText: "PropertyKeysEmptyString", icon: "nodes/property.svg"}
// EXIST: { lookupString: "bar.baz", itemText: "bar.baz", tailText: "=2", typeText: "PropertyKeysEmptyString", icon: "nodes/property.svg"}
// EXIST: { lookupString: "foo.bar.baz", itemText: "foo.bar.baz", tailText: "=3", typeText: "PropertyKeysEmptyString", icon: "nodes/property.svg"}
// EXIST: { lookupString: "foo.test", itemText: "foo.test", tailText: "=4", typeText: "PropertyKeysEmptyString", icon: "nodes/property.svg"}
// NOTHING_ELSE
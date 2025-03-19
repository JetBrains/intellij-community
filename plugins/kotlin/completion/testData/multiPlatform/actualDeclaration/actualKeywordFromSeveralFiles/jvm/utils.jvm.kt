actual fun baz(): String = "baz"

actual<caret>

// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, foo", "itemText": "actual fun foo() {...}" }
// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, bar", "itemText": "actual fun bar() {...}" }
// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, bar2", "itemText": "actual fun bar2() {...}" }
// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, bar3", "itemText": "actual fun bar3() {...}" }
// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, baz2", "itemText": "actual fun baz2(): String {...}" }
// ABSENT: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, baz", "itemText": "actual fun baz(): String {...}" }
// ABSENT: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, utils", "itemText": "actual fun utils(): Utils {...}" }
// ABSENT: { "lookupString": "actual", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "actual, name", "itemText": "actual val name: String" }
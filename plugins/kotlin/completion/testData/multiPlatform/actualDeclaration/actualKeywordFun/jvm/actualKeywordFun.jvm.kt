actual fun baz(): String = "baz"

actual<caret>

// EXIST: {"lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, foo", "itemText": "actual fun foo() {...}"}
// EXIST: {"lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, bar", "itemText": "actual fun bar(): Int {...}"}
// ABSENT: {"lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, baz", "itemText": "actual fun baz(): String {...}"}
actual fun bazzz(): String = "bazzz"

actual fun ba<caret>

// EXIST: {"lookupString": "bar", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "bar", "itemText": "actual fun bar() {...}"}
// EXIST: {"lookupString": "baz", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "baz", "itemText": "actual fun baz(): Int {...}"}
// ABSENT: {"lookupString": "bazzz", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "bazzz", "itemText": "actual fun bazzz(): String {...}"}
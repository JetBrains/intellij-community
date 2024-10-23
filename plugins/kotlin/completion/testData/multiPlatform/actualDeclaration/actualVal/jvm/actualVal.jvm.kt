actual val bazzz: String = "bazzz"

actual val ba<caret>

// EXIST: {"lookupString": "bar", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "bar", "itemText": "actual val bar: Int"}
// EXIST: {"lookupString": "baz", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "baz", "itemText": "actual val baz: Float"}
// ABSENT: {"lookupString": "bazzz", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "bazzz", "itemText": "actual val bazzz: String"}
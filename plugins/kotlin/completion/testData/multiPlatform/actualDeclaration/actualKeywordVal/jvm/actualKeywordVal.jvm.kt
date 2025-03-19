actual val baz: String = "baz"

actual<caret>

// EXIST: {"lookupString": "actual", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "actual, foo", "itemText": "actual val foo: Int"}
// EXIST: {"lookupString": "actual", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "actual, bar", "itemText": "actual val bar: Float"}
// ABSENT: {"lookupString": "actual", "module": "testModule_Common", "icon": "org/jetbrains/kotlin/idea/icons/field_value.svg", "allLookupStrings": "actual, baz", "itemText": "actual val baz: String"}
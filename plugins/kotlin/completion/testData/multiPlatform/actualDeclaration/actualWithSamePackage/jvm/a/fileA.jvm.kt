package a

actual fun fromPackageA_2(): String = ""

actual<caret>

// EXIST: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, fromPackageA_1", "itemText": "actual fun fromPackageA_1() {...}" }
// ABSENT: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, fromPackageA_2", "itemText": "actual fun fromPackageA_2(): String {...}" }
// ABSENT: { "lookupString": "actual", "module": "testModule_Common", "icon": "Function", "allLookupStrings": "actual, fromPackageB", "itemText": "actual fun fromPackageB(): Boolean {...}" }
// NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS: 5
package test

import test.MyEnum.*

enum class MyEnum {
    ENTRY;
}

class Unrelated {
    val firstEntry = ENTRY
    val myEntries = entries
    val myValueOf = valueOf("ENTRY")
    val myValues = values()
}
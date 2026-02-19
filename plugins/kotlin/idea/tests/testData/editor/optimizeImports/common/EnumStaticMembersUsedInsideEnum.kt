package test

import test.MyEnum.ENTRY
import test.MyEnum.entries
import test.MyEnum.valueOf
import test.MyEnum.values

enum class MyEnum {
    ENTRY;

    companion object {
        val firstEntry = ENTRY
        val myEntries = entries
        val myValueOf = valueOf("ENTRY")
        val myValues = values()
    }
}
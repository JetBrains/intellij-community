package test

import test.MySpecialEnum.MySpecialEntry
import test.RegularEnum.RegularEntry

interface MyInterface

class MySpecialObject : MyInterface

class RegularObject

class MySpecialClassWithCompanion {
    companion object : MyInterface
}

class MySpecialClassWithPrivateCompanion {
    private companion object : MyInterface
}

class RegularClassWithCompanion {
    companion object
}

enum class MySpecialEnum : MyInterface {
    MySpecialEntry
}

enum class RegularEnum {
    RegularEntry
}

fun action(myInterface: MyInterface) {}

fun usage() {
    action(myInterface = <caret>)
}

// EXIST: MySpecialObject
// ABSENT: RegularObject

// see KTIJ-28962 for why MySpecialClassWithCompanion is not yet completed
// ABSENT: MySpecialClassWithCompanion
// ABSENT: MySpecialClassWithPrivateCompanion
// ABSENT: RegularClassWithCompanion

// EXIST: MySpecialEntry
// ABSENT: RegularEntry


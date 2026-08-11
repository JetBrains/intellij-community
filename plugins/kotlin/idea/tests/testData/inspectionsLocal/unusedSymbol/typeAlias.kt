// PROBLEM: none
// K2_ERROR: TYPEALIAS_AS_CALLABLE_QUALIFIER_IN_IMPORT_ERROR

import Other.HELLO

val ONE = HELLO

enum class MyEnum {
    HELLO,
    WORLD
}

typealias Other<caret> = MyEnum
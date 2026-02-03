// PROBLEM: none
// K2_ERROR: Cannot use typealias 'Other' as a callable qualifier in import. Use original class 'MyEnum' instead or rewrite calls with 'Other' as a qualifier. See https://youtrack.jetbrains.com/issue/KT-64431.

import Other.HELLO

val ONE = HELLO

enum class MyEnum {
    HELLO,
    WORLD
}

typealias Other<caret> = MyEnum
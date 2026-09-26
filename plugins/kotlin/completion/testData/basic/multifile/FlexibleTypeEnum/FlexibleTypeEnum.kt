package a

enum class EnumClass {
    FOO, BOO
}

fun test() {
    JavaClass.test(<caret>)
}

// EXIST: FOO
// EXIST: BOO

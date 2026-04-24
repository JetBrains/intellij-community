package a

sealed interface SealedClass {
    class Foo : SealedClass
    class Boo : SealedClass
}

fun test() {
    JavaClass.test(<caret>)
}

// EXIST: Foo
// EXIST: Boo
// IGNORE_K1

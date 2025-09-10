class A
class B: Exception()
class C

fun test() {
    try {

    } catch (e: <caret>) {

    }
}

// WITH_ORDER
// EXIST: B
// EXIST: A
// EXIST: C

// IGNORE_K1
// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Base
class Derived : Base
interface Other

context(_: Base)
fun fooBase() {}

context(_: Other)
fun fooOther() {}

fun test() {
    with(Derived()) {
        foo<caret>
    }
}

// EXIST: fooBase
// ABSENT: fooOther

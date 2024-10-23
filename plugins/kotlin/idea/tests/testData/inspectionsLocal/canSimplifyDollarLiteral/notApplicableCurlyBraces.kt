// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none

fun test() {
    $$"\$$<caret>{Foo}"
}

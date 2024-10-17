// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// IGNORE_K1
// PROBLEM: none

fun test() {
    $$"\$$<caret>{Foo}"
}

// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// PROBLEM: none
fun foo() {
    val x = "x"
    val y = $$"$$<caret>{x.length}"
}

/* Can't enable K2 features in K1 mode by flag and can't ignore K1 via IGNORE_K1 because: "Looks like the test passes" is then reported */
// DISABLE_ERRORS

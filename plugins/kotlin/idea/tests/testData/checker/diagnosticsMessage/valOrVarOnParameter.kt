// WITH_STDLIB

class A {
    constructor(<error descr="[VAL_OR_VAR_ON_SECONDARY_CONSTRUCTOR_PARAMETER]">val</error> <warning descr="[UNUSED_PARAMETER]">x</warning>: Int) {
        for (<error descr="[VAL_OR_VAR_ON_LOOP_PARAMETER]">val</error> z in 1..4) {}
    }

    fun foo(<error descr="[VAL_OR_VAR_ON_FUN_PARAMETER]">var</error> <warning descr="[UNUSED_PARAMETER]">y</warning>: Int) {
        try {
            for (<error descr="[VAL_OR_VAR_ON_LOOP_PARAMETER]">var</error> (<warning descr="[UNUSED_VARIABLE]">i</warning>, <warning descr="[UNUSED_VARIABLE]">j</warning>) in listOf(1 to 4)) {}
        } catch (<error descr="[VAL_OR_VAR_ON_CATCH_PARAMETER]">val</error> e: Exception) {
        }
    }
}
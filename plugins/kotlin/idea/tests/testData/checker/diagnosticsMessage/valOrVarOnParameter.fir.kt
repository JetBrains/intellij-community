// WITH_STDLIB

class A {
    constructor(<warning descr="[VAL_OR_VAR_ON_SECONDARY_CONSTRUCTOR_PARAMETER]">val</warning> <warning descr="[UNUSED_PARAMETER]">x</warning>: Int) {
        for (<warning descr="[VAL_OR_VAR_ON_LOOP_PARAMETER]">val</warning> z in 1..4) {}
    }

    fun foo(<warning descr="[VAL_OR_VAR_ON_FUN_PARAMETER]">var</warning> <warning descr="[UNUSED_PARAMETER]">y</warning>: Int) {
        try {
            for (<warning descr="[VAL_OR_VAR_ON_LOOP_PARAMETER]">var</warning> (<warning descr="[UNUSED_VARIABLE]">i</warning>, <warning descr="[UNUSED_VARIABLE]">j</warning>) in listOf(1 to 4)) {}
        } catch (<warning descr="[VAL_OR_VAR_ON_CATCH_PARAMETER]">val</warning> e: Exception) {
        }
    }
}
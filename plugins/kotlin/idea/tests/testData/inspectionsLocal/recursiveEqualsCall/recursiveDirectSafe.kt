// ERROR: Type mismatch: inferred type is Boolean? but Boolean was expected
// ERROR: Type mismatch: inferred type is Boolean? but Boolean was expected
// K2_ERROR: CONDITION_TYPE_MISMATCH

class Test {
    override fun equals(other: Any?): Boolean {
        if (this?.equals<caret>(other)) return true
        return false
    }
}
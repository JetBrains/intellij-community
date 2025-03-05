// ERROR: Type mismatch: inferred type is Boolean? but Boolean was expected
// ERROR: Type mismatch: inferred type is Boolean? but Boolean was expected

class Test {
    override fun equals(other: Any?): Boolean {
        if (this?.equals<caret>(other)) return true
        return false
    }
}
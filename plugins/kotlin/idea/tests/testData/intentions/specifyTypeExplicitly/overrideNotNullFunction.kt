// IGNORE_FIR
// CHOOSE_NULLABLE_TYPE_IF_EXISTS
// WITH_STDLIB
interface Base {
    fun notNullFun(): String
}

class Tesst : Base {
    override fun notNullFun()<caret> = java.lang.String.valueOf("")
}

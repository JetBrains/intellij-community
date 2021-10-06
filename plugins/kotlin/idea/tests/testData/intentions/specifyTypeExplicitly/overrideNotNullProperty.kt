// CHOOSE_NULLABLE_TYPE_IF_EXISTS
// WITH_RUNTIME
interface Base {
    val notNull: String
}

class Test : Base {
    override val notNull<caret> = java.lang.String.valueOf("")
}

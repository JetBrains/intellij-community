// "Safe delete 'WORLD'" "true"
enum class MyEnum(val i: Int) {
    WORLD<caret>("42"),
    HELLO(42)
    ;

    constructor(s: String): this(42)
}

fun test() {
    MyEnum.HELLO
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix
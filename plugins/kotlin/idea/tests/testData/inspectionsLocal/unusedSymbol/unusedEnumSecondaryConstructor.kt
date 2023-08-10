enum class MyEnum(val i: Int) {
    HELLO(42)
    ;

    private constructor<caret>(s: String): this(42)
}

fun test() {
    MyEnum.HELLO
}

// IGNORE_FE10
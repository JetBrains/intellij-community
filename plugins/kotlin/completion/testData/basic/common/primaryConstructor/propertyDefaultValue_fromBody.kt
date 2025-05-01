class X(
    val property: Int = <caret>,
) {
    fun memberFun(): Int = 10
    val memberProperty: Int = 10
}

// EXIST: memberFun
// EXIST: memberProperty

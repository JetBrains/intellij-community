class X(
    val property: Int = ext<caret>,
)

fun X.extFun(): Int = 10
val X.extProperty: Int get() = 10

// EXIST: extFun
// EXIST: extProperty

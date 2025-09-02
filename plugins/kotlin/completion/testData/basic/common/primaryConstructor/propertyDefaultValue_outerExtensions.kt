// IGNORE_K1

class X(
    val property: Int = ext<caret>,
)

fun X.extFun(): Int = 10
val X.extProperty: Int get() = 10

// ABSENT: extFun
// ABSENT: extProperty

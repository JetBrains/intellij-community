class X(
    val property: Int = base<caret>,
) : Base()

open class Base {
    fun memberFunFromBase(): Int = 10
    val memberPropertyFromBase: Int = 10

    fun Base.extFunFromBase(): Int = 10
    val Base.extPropertyFromBase: Int get() = 10
}

// EXIST: memberFunFromBase
// EXIST: memberPropertyFromBase
// EXIST: extFunFromBase
// EXIST: extPropertyFromBase

@Deprecated<error descr="[NO_VALUE_FOR_PARAMETER] No value passed for parameter 'message'">()</error>
fun foo() {}
@Deprecated(<error descr="[CONSTANT_EXPECTED_TYPE_MISMATCH] The boolean literal does not conform to the expected type String">false</error>)
fun boo() {}

fun far() = <warning descr="[DEPRECATION] 'foo(): Unit' is deprecated. ">foo</warning>()

fun bar() = <warning descr="[DEPRECATION] 'boo(): Unit' is deprecated. ">boo</warning>()

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS

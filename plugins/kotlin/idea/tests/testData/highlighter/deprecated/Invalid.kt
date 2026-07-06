@Deprecated<error descr="[NO_VALUE_FOR_PARAMETER]">()</error>
fun foo() {}
@Deprecated(<error descr="[CONSTANT_EXPECTED_TYPE_MISMATCH]">false</error>)
fun boo() {}

fun far() = <warning descr="[DEPRECATION]">foo</warning>()

fun bar() = <warning descr="[DEPRECATION]">boo</warning>()

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS

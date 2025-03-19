// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test(a: Any) {
    $$"foo$$a$${$$$$"bar$$baz"}boo$bee$$${"safe"}$$$${"unsafe"}"<caret>
}

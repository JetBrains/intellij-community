class C {
    operator fun get(pInt: Int, pString: String){}
}

fun foo(c: C) {
    c[<caret>]
}

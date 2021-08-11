class C {
    operator fun get(pInt: Int, pString: String){}
}

fun foo(c: C) {
    c[<caret>]
}

// TYPE: "1, "

//Text: (pInt: Int, <highlight>pString: String</highlight>), Disabled: false, Strikeout: false, Green: true
fun foo(vararg p0: String, p1: Int, p2: Int) {}

fun test() {
    foo("", p1 = 1, <caret>)
}

/*
Text: (<disabled>vararg p0: String, [p1: Int],</disabled><highlight> </highlight>[p2: Int]), Disabled: false, Strikeout: false, Green: true
*/
// EXPECTED_DUPLICATED_HIGHLIGHTING

fun <symbolName descr="null">bar</symbolName>(<symbolName descr="null">block</symbolName>: () -> <symbolName descr="null">Int</symbolName>) = <symbolName descr="null"><symbolName descr="null">block</symbolName></symbolName>()

fun <symbolName descr="null">foo</symbolName>(): <symbolName descr="null">Int</symbolName> {
    <symbolName descr="null"><symbolName descr="null">bar</symbolName></symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_LABEL">label@</symbolName> {
        return<symbolName descr="null" textAttributesKey="KOTLIN_LABEL">@label</symbolName> 2
    }

    <symbolName descr="null" textAttributesKey="KOTLIN_LABEL">loop@</symbolName> for (<symbolName descr="null">i</symbolName> in 1..100) {
        break<symbolName descr="null" textAttributesKey="KOTLIN_LABEL">@loop</symbolName>
    }

    <symbolName descr="null" textAttributesKey="KOTLIN_LABEL">loop2@</symbolName> for (<symbolName descr="null">i</symbolName> in 1..100) {
        break<error descr="There should be no space or comments before '@' in label reference"> </error><symbolName descr="null" textAttributesKey="KOTLIN_LABEL">@loop2</symbolName>
    }

    return 1
}
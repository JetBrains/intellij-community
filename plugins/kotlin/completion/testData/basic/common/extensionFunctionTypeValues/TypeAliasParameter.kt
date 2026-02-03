// FIR_COMPARISON
typealias MyInt = Int

fun test(i: Int?, foo: Int.(MyInt) -> Char) {
    i?.fo<caret>
}
// EXIST: { lookupString: "foo", itemText: "foo", tailText: "(MyInt /* = Int */)", typeText: "Char" }
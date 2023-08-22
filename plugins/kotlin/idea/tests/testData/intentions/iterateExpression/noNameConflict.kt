// AFTER-WARNING: Variable 'name1' is never used
// AFTER-WARNING: Variable 'name2' is never used
fun foo(names: List<String>, name: String) {
    if (name == "") {
        val name2 = ""
    }

    names<caret>

    val name1 = ""
}
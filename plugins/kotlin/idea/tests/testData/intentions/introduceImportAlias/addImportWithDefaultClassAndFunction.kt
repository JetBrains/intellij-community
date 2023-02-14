// WITH_STDLIB
// AFTER-WARNING: Variable 'list' is never used
// AFTER-WARNING: Variable 'secondList' is never used
fun foo() {
    val list: List<String>
    val secondList = List<caret>(5) { 1 }
}

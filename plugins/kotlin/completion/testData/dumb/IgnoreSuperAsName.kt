// it should not show super as a name because it is a reserved keyword
fun main() {
    val a = super
    val b = supe<caret>
}

// EXIST: {"lookupString":"super","attributes":"bold","allLookupStrings":"super","itemText":"super"}
// NOTHING_ELSE
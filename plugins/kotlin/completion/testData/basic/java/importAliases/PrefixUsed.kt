import java.util.ArrayList as JavaList

fun foo(): Ja<caret>

// IGNORE_K2
// EXIST: { lookupString: "JavaList", itemText: "JavaList", icon: "RowIcon(icons=[Class, null])"}

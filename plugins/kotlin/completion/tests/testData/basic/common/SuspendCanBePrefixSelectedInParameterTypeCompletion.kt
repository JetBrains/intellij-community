class SomeType

class someRareTypeStartingWithSmallLetter

fun test(a: s<caret>) {}

// WITH_ORDER
// EXIST: { itemText: "someRareTypeStartingWithSmallLetter" }
// EXIST: { itemText: "suspend" }
// ABSENT: { itemText: "SomeType" }

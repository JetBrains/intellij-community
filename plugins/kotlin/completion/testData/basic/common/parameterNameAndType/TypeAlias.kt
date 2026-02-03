// FIR_COMPARISON
// FIR_IDENTICAL
typealias AliasedLong = Long

fun foo(ali<caret>) {}

// EXIST: { lookupString: "aliasedLong: AliasedLong", itemText: "aliasedLong: AliasedLong", tailText: " (<root>)", typeText: "Long", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}

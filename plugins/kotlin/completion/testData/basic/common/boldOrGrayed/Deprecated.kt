@Deprecated("message")
fun deprecatedFun() {}

@Deprecated("message")
val deprecatedProperty = 1

@Deprecated("message")
class deprecatedClass

fun test() {
    deprecated<caret>
}

// EXIST: {"lookupString":"deprecatedFun","tailText":"() (<root>)","typeText":"Unit","icon":"Function","attributes":"strikeout"}
// EXIST: {"lookupString":"deprecatedProperty","tailText":" (<root>)","typeText":"Int","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg","attributes":"strikeout"}
// EXIST: {"lookupString":"deprecatedClass","tailText":" (<root>)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"strikeout"}
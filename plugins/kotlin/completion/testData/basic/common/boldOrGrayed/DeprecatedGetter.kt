@get:Deprecated("")
val Int.foo: Int
    get() = 1

fun test(n: Int) {
    n.f<caret>
}

// EXIST: {"lookupString":"foo","tailText":" for Int in <root>","typeText":"Int","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg","attributes":"bold strikeout"}
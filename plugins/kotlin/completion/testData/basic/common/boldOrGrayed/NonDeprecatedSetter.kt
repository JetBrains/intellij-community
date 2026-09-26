@get:Deprecated("")
var Int.foo: Int
    get() = 1
    set(value) {}

fun test(n: Int) {
    n.f<caret>
}

// EXIST: {"lookupString":"foo","tailText":" for Int in <root>","typeText":"Int","icon":"org/jetbrains/kotlin/idea/icons/field_variable.svg","attributes":"bold"}
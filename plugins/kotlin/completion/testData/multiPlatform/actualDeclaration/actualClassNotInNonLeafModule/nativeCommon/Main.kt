
fun test() {
    ReallyLongClassN<caret>
}

// EXIST: {"lookupString":"ReallyLongClassName","tailText":" (a)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"ReallyLongClassName","itemText":"ReallyLongClassName"}
// NOTHING_ELSE
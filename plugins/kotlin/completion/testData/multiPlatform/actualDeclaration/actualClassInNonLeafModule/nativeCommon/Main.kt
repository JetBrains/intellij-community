
fun test() {
    ReallyLongClassN<caret>
}

// IGNORE_K1
// We are not checking the module because it could be either the one for the actual or the one for the expect declaration, but only one of them should be displayed.
// EXIST: {"lookupString":"ReallyLongClassName","tailText":" (a)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","allLookupStrings":"ReallyLongClassName","itemText":"ReallyLongClassName"}
// NOTHING_ELSE
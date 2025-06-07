
fun test() {
    reallyLongFunctionN<caret>
}

// IGNORE_K1
// We are not checking the module because it could be either the one for the actual or the one for the expect declaration, but only one of them should be displayed.
// EXIST: {"lookupString":"reallyLongFunctionName","tailText":"() (a)","typeText":"Unit","icon":"Function","attributes":"","allLookupStrings":"reallyLongFunctionName","itemText":"reallyLongFunctionName"}
// NOTHING_ELSE
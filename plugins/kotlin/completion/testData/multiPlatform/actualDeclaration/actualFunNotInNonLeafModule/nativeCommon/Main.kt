
fun test() {
    reallyLongFunctionN<caret>
}

// EXIST: {"lookupString":"reallyLongFunctionName","tailText":"() (a)","typeText":"Unit","icon":"Function","attributes":"","allLookupStrings":"reallyLongFunctionName","itemText":"reallyLongFunctionName"}
// NOTHING_ELSE
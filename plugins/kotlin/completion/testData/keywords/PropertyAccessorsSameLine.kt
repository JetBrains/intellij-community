// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+MultiPlatformProjects

class Some {
    var a : Int <caret>
}

// EXIST:  {"lookupString":"by","attributes":"bold","allLookupStrings":"by","itemText":"by"}
// EXIST:  {"lookupString":"get","attributes":"bold","allLookupStrings":"get","itemText":"get"}
// EXIST:  {"lookupString":"get","tailText":"() = ...","attributes":"bold","allLookupStrings":"get","itemText":"get"}
// EXIST:  {"lookupString":"get","tailText":"() {...}","attributes":"bold","allLookupStrings":"get","itemText":"get"}
// EXIST:  {"lookupString":"set","attributes":"bold","allLookupStrings":"set","itemText":"set"}
// EXIST:  {"lookupString":"set","tailText":"(value) = ...","attributes":"bold","allLookupStrings":"set","itemText":"set"}
// EXIST:  {"lookupString":"set","tailText":"(value) {...}","attributes":"bold","allLookupStrings":"set","itemText":"set"}
// NOTHING_ELSE

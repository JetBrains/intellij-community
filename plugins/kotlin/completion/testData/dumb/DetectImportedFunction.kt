val a = some<caret>

fun test() {
    someGlobalFunction(123)
}


// EXIST: { lookupString:"someGlobalFunction", tailText:"()" }
// NOTHING_ELSE
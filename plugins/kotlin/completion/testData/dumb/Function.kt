fun someFunction() {

}

val a = some<caret>

// EXIST: { lookupString:"someFunction", tailText:"()" }
// NOTHING_ELSE
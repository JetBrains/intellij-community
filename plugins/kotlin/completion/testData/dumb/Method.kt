class Test {
    val a = some<caret>

    fun someFunction() {

    }
}

// EXIST: { lookupString:"someFunction", tailText:"()" }
// NOTHING_ELSE
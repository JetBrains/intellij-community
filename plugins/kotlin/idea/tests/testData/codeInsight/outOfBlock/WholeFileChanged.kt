// OUT_OF_CODE_BLOCK: TRUE
// TYPE: }
// SKIP_ANALYZE_CHECK
// ERROR: Function declaration must have a name

// It is kind of false positive as `}` breaks entire PSI and all PSI elements are recreated, even (implicit) package directive
// PACKAGE_CHANGE

fun test() {<caret>

    val someThing: Any? = null
    val otherThing: Any? = null

    if (!(someThing?.equals(otherThing) ?: otherThing == null)) {
        // Some comment
    }


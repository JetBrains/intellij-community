// OUT_OF_CODE_BLOCK: TRUE

// It is kind of false positive as `}` breaks entire PSI and all PSI elements are recreated, even (implicit) package directive
// PACKAGE_CHANGE

// ERROR: Function 'test' must have a body
// TYPE: \b

fun test() {<caret>

}

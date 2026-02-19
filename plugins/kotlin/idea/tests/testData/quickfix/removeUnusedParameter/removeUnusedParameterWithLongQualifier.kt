// "Remove parameter 'a'" "true"
// DISABLE_ERRORS

package com.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy.zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz

class SomeVeryImportantClass
fun message(<caret>a: SomeVeryImportantClass, b: SomeVeryImportantClass, c: SomeVeryImportantClass) = Unit
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix
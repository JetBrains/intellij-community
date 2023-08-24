// "Create parameter 'foo'" "true"
// ACTION: Convert to multi-line lambda
// ACTION: Create local variable 'foo'
// ACTION: Create object 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename reference

fun test(n: Int) {
    val f = { a: Int, b: Int -> <caret>foo }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
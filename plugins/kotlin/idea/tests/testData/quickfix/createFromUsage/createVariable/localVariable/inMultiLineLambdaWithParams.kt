// "Create local variable 'foo'" "true"
// ACTION: Convert to single-line lambda
// ACTION: Create local variable 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename reference
// ACTION: Specify explicit lambda signature

fun test(n: Int) {
    val f: (Int, Int) -> Int = { a, b ->
        <caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction
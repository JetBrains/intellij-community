// "Add annotation target" "false"
// ACTION: Compiler warning 'INAPPLICABLE_TARGET_ON_PROPERTY_WARNING' options
// ACTION: Create test
// ACTION: Extract 'Test' from current file
// ACTION: Make internal
// ACTION: Make private
// ERROR: This annotation is not applicable to target 'class' and use site target '@get'
annotation class Ann

<caret>@get:Ann
class Test(val foo: String)

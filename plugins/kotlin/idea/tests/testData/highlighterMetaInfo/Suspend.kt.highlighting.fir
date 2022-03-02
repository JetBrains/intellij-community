val <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>fnType<!> : <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!> = {}

val <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>fnFnType<!>: () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!> = {  -> {}}

<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>inSuspend<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>fn<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {
    val <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>res<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> (<!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>) -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!> = { <!HIGHLIGHTING("descr='Automatically declared based on the expected type'; textAttributesKey='KOTLIN_CLOSURE_DEFAULT_PARAMETER'")!>it<!> + 1 };
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>T2<!>().<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>nonSuspend<!>()
    .<!HIGHLIGHTING("textAttributesKey='KOTLIN_SUSPEND_FUNCTION_CALL'")!>suspend1<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>fn<!>)
    .<!HIGHLIGHTING("textAttributesKey='KOTLIN_SUSPEND_FUNCTION_CALL'")!>suspend1<!> {  }
        .<!HIGHLIGHTING("textAttributesKey='KOTLIN_SUSPEND_FUNCTION_CALL'")!>suspend1<!> { <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>res<!>(5) }
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>res<!>(5)
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>fnType<!>()
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>fnFnType<!>().<!HIGHLIGHTING("textAttributesKey='KOTLIN_SUSPEND_FUNCTION_CALL'")!>invoke<!>()
}
class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>T2<!> {
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>inline<!> fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>suspend1<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>block<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>suspend<!> () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>): <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>T2<!> {
        <!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>block<!>()
        return this
    }
    fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>nonSuspend<!>() = this
}

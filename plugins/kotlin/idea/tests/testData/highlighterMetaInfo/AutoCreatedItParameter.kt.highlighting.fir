fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    val <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>vect<!> = <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CONSTRUCTOR'")!>MyIterable<!><<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!>>()

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>vect<!>
        .<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_CALL'")!>filter<!> { <!HIGHLIGHTING("severity='INFORMATION'; descr='Automatically declared based on the expected type'; textAttributesKey='KOTLIN_CLOSURE_DEFAULT_PARAMETER'")!>it<!> != 2 }
        .<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_CALL'")!>forEach<!> { <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>it<!>: <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!> ->
            <!HIGHLIGHTING("severity='INFORMATION'; descr='Automatically declared based on the expected type'; textAttributesKey='KOTLIN_CLOSURE_DEFAULT_PARAMETER'")!>it<!>.<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_CALL'")!>toString<!>()
        }
}

class <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>MyIterable<!><<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>> {
    fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>filter<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>function<!>: (<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>) -> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Boolean<!>) = this
    fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>forEach<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>action<!>: (<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>) -> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {
    }
}

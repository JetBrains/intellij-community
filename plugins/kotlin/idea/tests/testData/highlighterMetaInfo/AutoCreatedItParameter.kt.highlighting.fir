fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    val <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>vect<!> = <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>MyIterable<!><<!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>>()

    <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>vect<!>
        .<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>filter<!> { <!HIGHLIGHTING("descr='Automatically declared based on the expected type'; textAttributesKey='KOTLIN_CLOSURE_DEFAULT_PARAMETER'")!>it<!> != 2 }
        .<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>forEach<!> { <!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>it<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!> ->
            <!HIGHLIGHTING("descr='Automatically declared based on the expected type'; textAttributesKey='KOTLIN_CLOSURE_DEFAULT_PARAMETER'")!>it<!>.<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>toString<!>()
        }
}

class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>MyIterable<!><<!HIGHLIGHTING("textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>> {
    fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>filter<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>function<!>: (<!HIGHLIGHTING("textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>) -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Boolean<!>) = this
    fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>forEach<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>action<!>: (<!HIGHLIGHTING("textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>) -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {
    }
}

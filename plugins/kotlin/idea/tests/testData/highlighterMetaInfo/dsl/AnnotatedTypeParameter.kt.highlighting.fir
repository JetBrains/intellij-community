<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@DslMarker<!>
<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@Target<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_ENUM'")!>AnnotationTarget<!>.<!HIGHLIGHTING("textAttributesKey='KOTLIN_ENUM_ENTRY'")!>TYPE<!>)
<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>annotation<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>Dsl3<!>

<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>open<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Super<!>

class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl<!> : <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Super<!>() {
    fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>bar<!>(): <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!> = 0
}

class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl2<!> : <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Super<!>() {
    fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>baz<!>(): <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!> = 1
}

fun <<!HIGHLIGHTING("textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>> <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>f<!>: (<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@Dsl3<!> <!HIGHLIGHTING("textAttributesKey='KOTLIN_TYPE_PARAMETER'")!>T<!>).() -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    <!HIGHLIGHTING("descr='Style 3'; textAttributesKey='KOTLIN_DSL_STYLE3'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>foo<!><<!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl<!>> {
        <!HIGHLIGHTING("descr='Style 3'; textAttributesKey='KOTLIN_DSL_STYLE3'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>foo<!><<!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl2<!>> {

        }
    }
}

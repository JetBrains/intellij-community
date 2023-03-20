<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@DslMarker<!>
<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>annotation<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>Dsl3<!>

<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@Dsl3<!>
interface <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Inter<!>

class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl<!> : <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Inter<!>

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>f<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl<!>.() -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    <!HIGHLIGHTING("descr='Style 3'; textAttributesKey='KOTLIN_DSL_STYLE3'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>foo<!> {

    }
}

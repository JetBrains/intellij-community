<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@DslMarker<!>
<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>annotation<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>Dsl1<!>

<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@DslMarker<!>
<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>annotation<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>Dsl2<!>

<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@Dsl1<!>
class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl1<!>

<!HIGHLIGHTING("textAttributesKey='KOTLIN_ANNOTATION'")!>@Dsl2<!>
class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl2<!>

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>f<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl1<!>.() -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>bar<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>f<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ForDsl2<!>.() -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_OBJECT'")!>Unit<!>) {}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    <!HIGHLIGHTING("descr='Style 1'; textAttributesKey='KOTLIN_DSL_STYLE1'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>foo<!> {
        <!HIGHLIGHTING("descr='Style 2'; textAttributesKey='KOTLIN_DSL_STYLE2'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>bar<!> {
            <!HIGHLIGHTING("descr='Style 2'; textAttributesKey='KOTLIN_DSL_STYLE2'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>bar<!> {
                <!HIGHLIGHTING("descr='Style 1'; textAttributesKey='KOTLIN_DSL_STYLE1'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>foo<!> {

                }
            }
        }
    }
}

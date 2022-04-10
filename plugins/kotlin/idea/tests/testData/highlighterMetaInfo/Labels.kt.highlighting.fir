fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>bar<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>block<!>: () -> <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>) = <!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>block<!>()

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(): <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!> {
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>bar<!> <!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>label@<!> {
        return<!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>@label<!> 2
    }

    <!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>loop@<!> for (<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>i<!> in 1..100) {
        break<!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>@loop<!>
    }

    <!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>loop2@<!> for (<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>i<!> in 1..100) {
        break<!HIGHLIGHTING("textAttributesKey='KOTLIN_LABEL'")!>@loop2<!>
    }

    return 1
}

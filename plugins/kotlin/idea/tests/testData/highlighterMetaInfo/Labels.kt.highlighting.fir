fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>bar<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>block<!>: () -> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!>) = <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'"), HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>block<!>()

fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(): <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!> {
    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>bar<!> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>label@<!> {
        return<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>@label<!> 2
    }

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>loop@<!> for (<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>i<!> in 1..100) {
        break<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>@loop<!>
    }

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>loop2@<!> for (<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>i<!> in 1..100) {
        break<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_LABEL'")!>@loop2<!>
    }

    return 1
}

// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

/*COMMENT*/
val items: Set<Int>
    field<caret> = mutableSetOf<Int>()
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

val cache: Map<String, Int>
    field<caret> = mutableMapOf()

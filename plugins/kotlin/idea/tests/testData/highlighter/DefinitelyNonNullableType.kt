// LANGUAGE_VERSION: 1.8

fun <<info textAttributesKey="KOTLIN_TYPE_PARAMETER">T</info>> <info textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</info>(<info textAttributesKey="KOTLIN_PARAMETER">x</info>: <info textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</info>) : <info textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</info> {
    val <info textAttributesKey="KOTLIN_LOCAL_VARIABLE">y</info>: <info textAttributesKey="KOTLIN_TYPE_PARAMETER">T & Any</info> = <info textAttributesKey="KOTLIN_PARAMETER">x</info>
    return <info textAttributesKey="KOTLIN_LOCAL_VARIABLE">y</info>
}

// COMPILER_ARGUMENTS: -XXLanguage:+DefinitelyNonNullableTypes
package pckg.dep

fun <T> (T & Any).defNotNull(f: T & Any): T & Any = null!!

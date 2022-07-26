
package qualified_expressions

fun test(s: IntRange?) {
   val <warning descr="[UNUSED_VARIABLE] Variable 'a' is never used">a</warning>: Int = <error descr="[TYPE_MISMATCH] Type mismatch: inferred type is Int? but Int was expected">s?.start</error>
   val b: Int? = s?.start
   val <warning>c</warning>: Int = s?.start ?: -11
   val <warning>d</warning>: Int = <error>s?.start ?: "empty"</error>
   val e: String = <error>s?.start ?: "empty"</error>
   val <warning>f</warning>: Int = s?.endInclusive ?: b ?: 1
   val <warning>g</warning>: Boolean? = e.startsWith("s")//?.length
}

fun String.startsWith(<warning>s</warning>: String): Boolean = true

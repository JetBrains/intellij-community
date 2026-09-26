package foo

class X {}

val s = <error descr="[EXPRESSION_EXPECTED_PACKAGE_FOUND]">java</error>
val ss = <error descr="[NO_COMPANION_OBJECT]">System</error>
val sss = <error descr="[NO_COMPANION_OBJECT]">X</error>
val x = "${<error descr="[NO_COMPANION_OBJECT]">System</error>}"
val xs = java.<error descr="[EXPRESSION_EXPECTED_PACKAGE_FOUND]">lang</error>
val xss = java.lang.<error descr="[NO_COMPANION_OBJECT]">System</error>
val xsss = foo.<error descr="[NO_COMPANION_OBJECT]">X</error>
val xssss = <error descr="[EXPRESSION_EXPECTED_PACKAGE_FOUND]">foo</error>
val f = { <error descr="[NO_COMPANION_OBJECT]">System</error> }

fun main() {
    <error descr="[EXPRESSION_EXPECTED_PACKAGE_FOUND]">java</error> = null
    <error descr="[NO_COMPANION_OBJECT]">System</error> = null
    <error descr="[NO_COMPANION_OBJECT]">System</error>!!
    java.lang.<error descr="[NO_COMPANION_OBJECT]">System</error> = null
    java.lang.<error descr="[NO_COMPANION_OBJECT]">System</error>!!
    <error descr="[NO_COMPANION_OBJECT]">System</error> is Int
    <error descr="[INVISIBLE_MEMBER]">System</error>()
    (<error descr="[NO_COMPANION_OBJECT]">System</error>)
    <warning descr="[REDUNDANT_LABEL_WARNING]">foo@</warning> <error descr="[NO_COMPANION_OBJECT]">System</error>
    null in <error descr="[NO_COMPANION_OBJECT]">System</error>
}

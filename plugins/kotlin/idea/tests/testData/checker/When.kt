fun Int.foo() : Boolean = true

fun foo() : Int {
    val s = ""
    val x = 1
    when (x) {
      is <error descr="[INCOMPATIBLE_TYPES] Incompatible types: String and Int">String</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      <warning descr="[USELESS_IS_CHECK] Check for instance is always 'false'">!is Int</warning> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      <warning descr="[USELESS_IS_CHECK] Check for instance is always 'true'">is Any<warning descr="[USELESS_NULLABLE_CHECK] Non-null type is checked for instance of nullable type">?</warning></warning> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      <error descr="[INCOMPATIBLE_TYPES] Incompatible types: String and Int">s</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      1 -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      1 + <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: a">a</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      in 1..<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: a">a</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      !in 1..<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: a">a</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
      else -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    }

    return 0
}

val _type_test : Int = foo() // this is needed to ensure the inferred return type of foo()

fun test() {
  val x = 1;
  val s = "";

  when (x) {
    <error descr="[INCOMPATIBLE_TYPES] Incompatible types: String and Int"><warning descr="[DUPLICATE_LABEL_IN_WHEN] Duplicate label in when">s</warning></error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    <error descr="[INCOMPATIBLE_TYPES] Incompatible types: String and Int">""</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    x -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    1 -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    else -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
  }

  val z = 1

  when (z) {
    <error descr="[ELSE_MISPLACED_IN_WHEN] 'else' entry must be the last one in a when-expression">else</error> -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
    <warning descr="[UNREACHABLE_CODE] Unreachable code">1 -> 2</warning>
  }

  when (<warning descr="[UNUSED_EXPRESSION] The expression is unused">z</warning>) {
    else -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
  }
}
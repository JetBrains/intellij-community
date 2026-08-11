fun Int.foo() : Boolean = true

fun foo() : Int {
    val s = ""
    val x = 1
    when (x) {
      is <error descr="[INCOMPATIBLE_TYPES]">String</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      <warning descr="[USELESS_IS_CHECK]">!is Int</warning> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      <warning descr="[USELESS_IS_CHECK]">is Any<warning descr="[USELESS_NULLABLE_CHECK]">?</warning></warning> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      <error descr="[INCOMPATIBLE_TYPES]">s</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      1 -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      1 + <error descr="[UNRESOLVED_REFERENCE]">a</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      in 1..<error descr="[UNRESOLVED_REFERENCE]">a</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      !in 1..<error descr="[UNRESOLVED_REFERENCE]">a</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
      else -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    }

    return 0
}

val _type_test : Int = foo() // this is needed to ensure the inferred return type of foo()

fun test() {
  val x = 1;
  val s = "";

  when (x) {
    <error descr="[INCOMPATIBLE_TYPES]"><warning descr="[DUPLICATE_LABEL_IN_WHEN]">s</warning></error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    <error descr="[INCOMPATIBLE_TYPES]">""</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    x -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    1 -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    else -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
  }

  val z = 1

  when (z) {
    <error descr="[ELSE_MISPLACED_IN_WHEN]">else</error> -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
    <warning descr="[UNREACHABLE_CODE]">1 -> 2</warning>
  }

  when (<warning descr="[UNUSED_EXPRESSION]">z</warning>) {
    else -> <warning descr="[UNUSED_EXPRESSION]">1</warning>
  }
}
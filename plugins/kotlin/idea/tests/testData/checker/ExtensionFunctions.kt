fun Int?.optint() : Unit {}
val Int?.optval : Unit get() = Unit

fun <T: Any, E> T.foo(<warning descr="[UNUSED_PARAMETER]">x</warning> : E, y : A) : T   {
  y.plus(1)
  y plus 1
  y + 1.0

  this<warning descr="[UNNECESSARY_SAFE_CALL]">?.</warning>minus<T>(this)

  return this
}

class A

infix operator fun A.plus(<warning descr="[UNUSED_PARAMETER]">a</warning> : Any) {

  1.foo()
  true.<error descr="[NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER]">foo</error><error descr="[NO_VALUE_FOR_PARAMETER]"><error descr="[NO_VALUE_FOR_PARAMETER]">()</error></error>

  <warning descr="[UNUSED_EXPRESSION]">1</warning>
}

infix operator fun A.plus(<warning descr="[UNUSED_PARAMETER]">a</warning> : Int) {
  <warning descr="[UNUSED_EXPRESSION]">1</warning>
}

fun <T> T.minus(<warning descr="[UNUSED_PARAMETER]">t</warning> : T) : Int = 1

fun test() {
  val <warning descr="[UNUSED_VARIABLE]">y</warning> = 1.abs
}
val Int.abs : Int
  get() = if (this > 0) this else -this;

<error descr="[EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT]">val <T> T.foo : T</error>

fun Int.foo() = this

// FILE: b.kt
//package null_safety

        fun parse(<warning descr="[UNUSED_PARAMETER]">cmd</warning>: String): Command? { return null  }
        class Command() {
        //  fun equals(other : Any?) : Boolean
          val foo : Int = 0
        }

        <error descr="[INAPPLICABLE_OPERATOR_MODIFIER]">operator</error> fun Any.<warning descr="[EXTENSION_SHADOWED_BY_MEMBER]">equals</warning>(<warning descr="[UNUSED_PARAMETER]">other</warning> : Any?) : Boolean = true
        fun Any?.equals1(<warning descr="[UNUSED_PARAMETER]">other</warning> : Any?) : Boolean = true
        fun Any.equals2(<warning descr="[UNUSED_PARAMETER]">other</warning> : Any?) : Boolean = true

        fun main(<warning descr="[UNUSED_PARAMETER]">args</warning>: Array<String>) {

            System.out.print(1)

            val command = parse("")

            command.foo

            command<error descr="[UNSAFE_CALL]">.</error>equals(null)
            command?.equals(null)
            command.equals1(null)
            command?.equals1(null)

            val c = Command()
            c<warning descr="[UNNECESSARY_SAFE_CALL]">?.</warning>equals2(null)

            if (command == null) <warning descr="[UNUSED_EXPRESSION]">1</warning>
        }

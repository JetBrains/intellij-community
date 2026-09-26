class A() {
  override fun equals(a : Any?) : Boolean = false
}

fun f(): Unit {
  var x: Int? = 1
  x = 1
  x + 1
  x.plus(1)
  x < 1
  x += 1

  x == 1
  x != 1

  <error descr="[EQUALITY_NOT_APPLICABLE]">A() == 1</error>

  <error descr="[EQUALITY_NOT_APPLICABLE]">x === "1"</error>
  <error descr="[EQUALITY_NOT_APPLICABLE]">x !== "1"</error>

  x === 1
  x !== 1

  x..2
  x in 1..2

  val y : Boolean? = true
  false || <error descr="[CONDITION_TYPE_MISMATCH]">y</error>
  <error descr="[CONDITION_TYPE_MISMATCH]">y</error> && true
  <error descr="[CONDITION_TYPE_MISMATCH]">y</error> && <error descr="[CONDITION_TYPE_MISMATCH]">1</error>
}

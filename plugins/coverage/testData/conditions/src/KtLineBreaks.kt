class KtLineBreaks {
  fun doSwitch(x: Int) {
    when
      (x) {
      1 -> println()
      2 -> println()
    }
  }

  fun doIf(x: Int) {
    if
      (
      x
      ==
      1
    )
      println("case 1")
  }

  fun f(x: Boolean) = x

  fun ifVariable(a: Boolean, b: Boolean, c: Boolean) {
    if (a
      && b
      && c) println("case 2")
  }

  fun ifMethods(a: Boolean, b: Boolean, c: Boolean) {
    if (f(a)
      && f(b)
      && f(c)) println("case 2")
  }

  fun andWithoutIfVariables(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return a
      && b
      && c
  }

  fun andWithoutIfMethods(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return f(a)
      && f(b)
      && f(c)
  }

  fun forCycle(n: Int) {
    for (i
    in
    0
      until
      n
    ) {
      println(i)
    }
  }

  fun forEachCycle(elements: Array<String?>) {
    for (
    e in
    elements
    ) {
      println(e)
    }
  }

  fun whileCycle(n: Int) {
    var i = 0
    while (
      i
      <
      n
    ) {
      println(i)
      i++
    }
  }

  fun doWhileCycle(n: Int) {
    var i = 0
    do {
      println(i)
    } while
      (
      i++
      <
      n
    )
  }

  private fun <T> T?.g(): T? = this
  private fun <T> h(x: T?): T? = x

  fun testNullCheckVariables(a: Int?, b: Int?) = a
      ?: b
      ?: 42

  fun testNullCheckMethods(a: Int?, b: Int?) = h(a)
    ?: h(b)
    ?: 42


  fun testSafeCallSequence(a: Int?) = a
    ?.g()
    ?.g()

  private fun <T> T?.veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongMethodName(): T? = this
  fun testSafeCallSequenceLongNames(a: Int?) = a
    ?.veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongMethodName()
    ?.veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongMethodName()
    ?.veryVeryVeryVeryVeryVeryVeryVeryVeryVeryVeryLongMethodName()
}

class KtConditions {
  fun oneBranch1(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    else {
      println("case 2")
    }
  }

  fun oneBranch2(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    else {
      println("case 2")
    }
  }

  fun allBranches(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    else {
      println("case 2")
    }
  }

  fun singleBranch1(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    println("case 2")
  }

  fun singleBranch2(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    println("case 2")
  }

  fun empty(x: Int) {
    if (x == 1) {
      println("case 1")
    }
    else {
      println("case 2")
    }
  }

  fun and1(a: Boolean, b: Boolean) {
    if (a && b) {
      println("both a and b are true")
    }
    else {
      println("either a or b is false")
    }
  }

  fun and2(a: Boolean, b: Boolean) {
    if (a && b) {
      println("both a and b are true")
    }
    else {
      println("either a or b is false")
    }
  }

  fun and3(a: Boolean, b: Boolean) {
    if (a && b) {
      println("both a and b are true")
    }
    else {
      println("either a or b is false")
    }
  }

  fun fullAnd(a: Boolean, b: Boolean) {
    if (a && b) {
      println("both a and b are true")
    }
    else {
      println("either a or b is false")
    }
  }

  fun or1(a: Boolean, b: Boolean) {
    if (a || b) {
      println("either a or b is true")
    }
    else {
      println("both a and b are false")
    }
  }

  fun or2(a: Boolean, b: Boolean) {
    if (a || b) {
      println("either a or b is true")
    }
    else {
      println("both a and b are false")
    }
  }

  fun or3(a: Boolean, b: Boolean) {
    if (a || b) {
      println("either a or b is true")
    }
    else {
      println("both a and b are false")
    }
  }

  fun fullOr(a: Boolean, b: Boolean) {
    if (a || b) {
      println("either a or b is true")
    }
    else {
      println("both a and b are false")
    }
  }

  fun andAnd0(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
      println("All true")
    }
    else {
      println("Some one is false")
    }
  }

  fun andAnd1(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
      println("All true")
    }
    else {
      println("Some one is false")
    }
  }

  fun andAnd2(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
      println("All true")
    }
    else {
      println("Some one is false")
    }
  }

  fun andAnd3(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
      println("All true")
    }
    else {
      println("Some one is false")
    }
  }

  fun negation(a: Boolean): Boolean {
    return !a
  }

  // condition is eliminated as the bytecode is the same as in the previous method
  fun manualNegation(a: Boolean): Boolean {
    return if (a == false) true else false
  }

  fun andWithoutIf(a: Boolean, b: Boolean): Boolean {
    return a && b
  }

  fun orWithoutIf(a: Boolean, b: Boolean): Boolean {
    return a || b
  }

  fun forCycle(n: Int) {
    for (i in 0 until n) {
      println(i)
    }
  }

  fun forEachCycle() {
    val elements = arrayOf("a", "b", "c")

    for (e in elements) {
      println(e)
    }
  }

  fun whileCycle(n: Int) {
    var i = 0
    while (i < n) {
      println(i)
      i++
    }
  }

  fun ternaryOperator1(a: Boolean): String {
    return if (a) "1" else "2"
  }

  fun ternaryOperator2(a: Boolean): String {
    return if (a) "1" else "2"
  }

  fun ternaryOperatorFull(a: Boolean): String {
    return if (a) "1" else "2"
  }

  fun ternaryOr(a: Boolean, b: Boolean): String {
    return if (a || b) "1" else "2"
  }

  fun doWhileCycle(n: Int) {
    var i = 0
    do {
      println(i)
    } while (i++ < n)
  }
}

class KtAllConditions {
  fun test1(a: Boolean, b: Boolean) {
    val res0 = if (a && b) 1 else 2
    val res1 = if (a || b) 1 else 2
  }

  fun test2(a: Boolean, b: Boolean, c: Boolean) {
    val res0 = if (a && b && c) 1 else 2
    val res1 = if (a || b && c) 1 else 2
    val res2 = if (a && (b || c)) 1 else 2
    val res3 = if (a || b || c) 1 else 2
    val res4 = if (a && b || c) 1 else 2
    val res5 = if ((a || b) && c) 1 else 2
  }

  fun test3(a: Boolean, b: Boolean, c: Boolean, d: Boolean) {
    val res0 = if (a && b && c && d) 1 else 2
    val res1 = if (a || b && c && d) 1 else 2
    val res2 = if (a && (b || c && d)) 1 else 2
    val res3 = if (a || b || c && d) 1 else 2
    val res4 = if (a && b && (c || d)) 1 else 2
    val res5 = if (a || b && (c || d)) 1 else 2
    val res6 = if (a && (b || c || d)) 1 else 2
    val res7 = if (a || b || c || d) 1 else 2
    val res8 = if (a && (b && c || d)) 1 else 2
    val res9 = if (a || b && c || d) 1 else 2
    val res10 = if (a && (b || c) && d) 1 else 2
    val res11 = if (a || (b || c) && d) 1 else 2
    val res12 = if (a && b || c && d) 1 else 2
    val res13 = if (a && b || c || d) 1 else 2
    val res14 = if ((a || b) && c && d) 1 else 2
    val res15 = if ((a || b) && (c || d)) 1 else 2
    val res16 = if (a && b && c || d) 1 else 2
    val res17 = if ((a || b && c) && d) 1 else 2
    val res18 = if (a && (b || c) || d) 1 else 2
    val res19 = if ((a || b || c) && d) 1 else 2
    val res20 = if ((a && b || c) && d) 1 else 2
    val res21 = if ((a || b) && c || d) 1 else 2
  }
}

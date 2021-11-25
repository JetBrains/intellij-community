// WITH_STDLIB
fun test(obj : Int) {
    if (obj > 10) {
        if (<warning descr="Condition 'obj in 1..5' is always false">obj in 1..5</warning>) {}
        if (<warning descr="Condition 'obj !in 1..5' is always true">obj !in 1..5</warning>) {}
    }
    if (obj in 1..10) {
        if (<warning descr="Condition 'obj !in 0..11' is always false">obj !in 0..11</warning>) {}
        if (<warning descr="Condition 'obj in 1..10' is always true">obj in 1..10</warning>) {}
        if (obj in 1 until 10) {}
        if (<warning descr="Condition 'obj in 1 until 11' is always true">obj in 1 until 11</warning>) {}
        if (<warning descr="Condition 'obj in 20..30' is always false">obj in 20..30</warning>) {}
        if (<warning descr="Condition 'obj in -10..-5' is always false">obj in -10..-5</warning>) {}
    }
    if (obj in 20..30) {
        if (<warning descr="Condition 'obj in 1..10' is always false">obj in 1..10</warning>) {}
    }
    if (1 in obj..10) {
        if (<warning descr="Condition 'obj > 10' is always false">obj > 10</warning>) {}
        if (<warning descr="Condition 'obj >= 10' is always false">obj >= 10</warning>) {}
        if (<warning descr="Condition 'obj > 1' is always false">obj > 1</warning>) {}
    }
}
fun test(a:Long, b:Long): Boolean {
    return a in 1 until b
}

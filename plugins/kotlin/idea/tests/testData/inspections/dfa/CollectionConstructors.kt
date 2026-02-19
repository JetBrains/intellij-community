// WITH_STDLIB
import kotlin.collections.*

fun simple() {
    val list1 = listOf<String>()
    if (<warning descr="Condition 'list1.isEmpty()' is always true">list1.isEmpty()</warning>) {}
    val list2 = emptyList<String>()
    if (<warning descr="Condition 'list2.isNotEmpty()' is always false">list2.isNotEmpty()</warning>) {}
    val list3 = listOf(1, 2, 3, 4)
    if (<warning descr="Condition 'list3.size == 4' is always true">list3.size == 4</warning>) {}
    val list4 = listOfNotNull(1, 2, 3, 4)
    if (<warning descr="Condition 'list4.size > 4' is always false">list4.size > 4</warning>) {}
    if (list4.size == 4) {}
    if (list4.size == 0) {}
    val set1 = emptySet<String>()
    if (<warning descr="Condition 'set1.isEmpty()' is always true">set1.isEmpty()</warning>) {}
    val set2 = setOf<String>()
    if (<warning descr="Condition 'set2.isEmpty()' is always true">set2.isEmpty()</warning>) {}
    val set3 = setOf(1, 2, 3, 1)
    if (<warning descr="Condition 'set3.size > 4' is always false">set3.size > 4</warning>) {}
    if (set3.size == 4) {}
    if (<warning descr="Condition 'set3.isEmpty()' is always false">set3.isEmpty()</warning>) {}
    val map1 = emptyMap<String, String>()
    if (<warning descr="Condition 'map1.isEmpty()' is always true">map1.isEmpty()</warning>) {}
    val map2 = mapOf<String, String>()
    if (<warning descr="Condition 'map2.isNotEmpty()' is always false">map2.isNotEmpty()</warning>) {}
    val map3 = mapOf(1 to 2, 3 to 4, 5 to 6, 7 to 8)
    if (<warning descr="Condition 'map3.size >= 5' is always false">map3.size >= 5</warning>) {}
    if (map3.size >= 4) {}
    if (<warning descr="Condition 'map3.size == 0' is always false">map3.size == 0</warning>) {}
}

fun mutableSimple(otherList: MutableList<Int>) {
    val list = mutableListOf<Int>()
    for(x in otherList) {
        list.add(x)
    }
    if (list.isEmpty()) {}
}

fun mutable(otherList: MutableList<Int>) {
    val list = mutableListOf<Int>()
    if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
    otherList.add(1)
    if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
    modifyList(list)
    if (list.isEmpty()) {}
}

class MutableField {
    var field = mutableListOf<Int>()

    fun mutable() {
        val list = mutableListOf<Int>()
        if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
        update()
        if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
        field = list
        if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
        update()
        if (list.isEmpty()) {}
    }

    fun update() {
        field.add(1)
    }
}

fun mutableAndObject() {
    val list = mutableListOf<Int>()
    val obj = object : Runnable {
        override fun run() {
            list.add(1)
        }
    }
    if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
    obj.run()
    if (list.isEmpty()) {}
}

fun mutableAndLambda() {
    val list = mutableListOf<Int>()
    val fn = {
        list.add(1)
    }
    if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
    fn()
    if (list.isEmpty()) {}
}

fun mutableCheckInLambda() {
    val list = mutableListOf<Int>()
    val fn = {
        if (list.size == 0) {}
    }
    if (<warning descr="Condition 'list.isEmpty()' is always true">list.isEmpty()</warning>) {}
    list.add(1)
    if (list.isEmpty()) {}
    fn()
    if (list.isEmpty()) {}
}

fun mutableInMethodReference(x: Int?) {
    val set = mutableSetOf<Int>()
    x?.let(set::add)
    if (set.size == 0) {}
}

fun modifyList(list: MutableList<Int>) {
    list.add(2)
}

fun fromArray(arr: Array<Int>) {
    val list = mutableListOf(*arr)
    if (list.isEmpty()) {}
    if (arr.size == 0) return
    val list2 = mutableListOf(*arr)
    if (<warning descr="Condition 'list2.isEmpty()' is always false">list2.isEmpty()</warning>) {}
}


// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import java.util.HashMap

fun firstFun() {
  val a = HashMap<Int, String>()
  a.toSort<caret>
}

// INVOCATION_COUNT: 1
// EXIST: { lookupString:"toSortedMap", itemText:"toSortedMap", tailText:"() for Map<out K, V> in kotlin.collections", icon: "Function"}
// EXIST: { lookupString:"toSortedMap", itemText:"toSortedMap", tailText:"(comparator: Comparator<in Int>) for Map<out K, V> in kotlin.collections", icon: "Function"}
// NOTHING_ELSE

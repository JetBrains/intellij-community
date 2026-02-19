package a

import a.O.uniqueName

object O {
    fun Int.uniqueName() {}
}

fun Int.test() {
    uniqueN<caret>
}

// EXIST: {"allLookupStrings":"uniqueName","itemText":"uniqueName","typeText":"Unit","icon":"Function","attributes":"bold"}
// NUMBER: 1
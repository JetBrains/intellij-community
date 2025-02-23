package com.example.app

import com.example.utils.DateUtils

fun <!LINE_MARKER!>main<!>() {
    val currentDate = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: DateUtils'")!>DateUtils<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>getCurrentDate<!>()
    println("Today's date is: $<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Resolved to error element'")!>currentDate<!>")
}

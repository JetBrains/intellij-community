package a

fun String?.nullSafeGreet(): String = this ?: "null"

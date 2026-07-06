annotation class MyTestAnnotation

fun unused(<warning descr="[UNUSED_PARAMETER]">p</warning>: Int) {

}

@MyTestAnnotation
fun unusedButAnnotated(p: Int) {

}


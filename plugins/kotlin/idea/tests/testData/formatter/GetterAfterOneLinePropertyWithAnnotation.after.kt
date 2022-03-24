annotation class A

val ok1 get() = ""

val ok2 get() = ""

@A
val bug1 get() = ""

@get:A
val bug2 get() = ""

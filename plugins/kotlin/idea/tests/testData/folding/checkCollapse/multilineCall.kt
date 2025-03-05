data class ABC<fold text='(...)' expand='true'>(
    val a: Int,
    val b: Int,
    val c: Int
)</fold>

fun test() <fold text='{...}' expand='true'>{
    ABC<fold text='(...)' expand='true'>(
        1,
        2,
        3
    )</fold>
}</fold>

fun bar(a: Int, b: Int) = a + b

// multi-lines call is foldable
val c = bar<fold text='(...)' expand='true'>(
    a = 1,
    b = 2
)</fold>

// single-line call is not foldable
val d = bar(a = 1, b = 2)

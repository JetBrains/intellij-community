package some.very.very.very.long.packagename

interface A

abstract class Some {
    abstract fun foo(x: A, y: A): A
}

class Other : Some() {
    <caret>
}
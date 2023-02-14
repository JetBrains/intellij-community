// WITH_STDLIB

val xx = 1.also { }.(fun Int.(x: Int, y: Int) = this + x + y)<caret>(2, 3)
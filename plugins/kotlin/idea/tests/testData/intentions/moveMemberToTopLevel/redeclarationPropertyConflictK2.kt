// SHOULD_FAIL_WITH: Following declarations would clash: to move property 'val f1: Int' and destination property 'val f1: Int' declared in scope default
// IGNORE_K1
object Test {
    val <caret>f1 = 1
}

val f1 = 1

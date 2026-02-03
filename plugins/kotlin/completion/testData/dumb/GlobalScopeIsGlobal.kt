val someEarlierProperty = 1

val a = some<caret>

val someProperty = 5
fun someFunction() {

}

// EXIST: someEarlierProperty, someProperty, someFunction
// NOTHING_ELSE
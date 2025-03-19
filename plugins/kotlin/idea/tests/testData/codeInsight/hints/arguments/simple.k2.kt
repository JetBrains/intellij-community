fun foo(name: String, index: Int) {

}

val f = foo(/*<# [simple.kt:8]name| = #>*/"name", /*<# [simple.kt:22]index| = #>*/42)
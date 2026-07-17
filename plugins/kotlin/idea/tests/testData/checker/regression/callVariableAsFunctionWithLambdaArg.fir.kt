fun f() {
    val g = 3
    <error descr="[UNRESOLVED_REFERENCE]">g</error>  { workingSet, customer ->
    }
}

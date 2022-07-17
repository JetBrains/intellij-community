// SUPPRESS_HIGHLIGHTING
fun foo() {
    <selection>install(XXX)</selection>

    <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: install">install</error>(<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: XXX">XXX</error>)
}
// MODE: receivers_params
val x = foo {/*<# this: |[kotlin.String:kotlin.fqn.class]String #>*//*<# it: |[kotlin.Int:kotlin.fqn.class]Int #>*/

}

fun foo(x: String.(Int) -> Unit) {

}
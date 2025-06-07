// MODE: all
fun id(x: suspend () -> Int)/*<# : |suspend| |(|) -> |[kotlin.Int:kotlin.fqn.class]Int #>*/ = x

fun id2(x: suspend (String, Int) -> Int)/*<# : |suspend| |(|[kotlin.String:kotlin.fqn.class]String|, |[kotlin.Int:kotlin.fqn.class]Int|) -> |[kotlin.Int:kotlin.fqn.class]Int #>*/ = x

fun id3(x: suspend String.() -> Unit)/*<# : |suspend| |[kotlin.String:kotlin.fqn.class]String|.|(|) -> |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ = x
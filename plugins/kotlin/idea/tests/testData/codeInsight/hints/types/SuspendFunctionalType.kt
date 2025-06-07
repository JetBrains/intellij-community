// MODE: all
fun id(x: suspend () -> Int)/*<# : |(|) |->| |[kotlin.Int:kotlin.fqn.class]Int #>*/ = x

fun id2(x: suspend (String, Int) -> Int)/*<# : |(|[kotlin.String:kotlin.fqn.class]String|, |[kotlin.Int:kotlin.fqn.class]Int|) |->| |[kotlin.Int:kotlin.fqn.class]Int #>*/ = x
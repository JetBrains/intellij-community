// MODE: all
import java.io.File

class Context {
    val schemas/*<# : |[kotlin.collections.Map:kotlin.fqn.class]Map|<|[kotlin.String:kotlin.fqn.class]String|, |[kotlin.String:kotlin.fqn.class]String|> #>*/ = File("it").walkTopDown().associate { it.name to it.readText() }
}
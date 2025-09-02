// MODE: all

fun bar(someThing: Boolean) {}

class A {
    fun func(parameter: String): String = "hello from A"
}

fun A.extension(parameter: Int): Int = 0

fun foo(action: (x: Int, y: String) -> String) {
    val actionRef/*<# : |(|x|: |[kotlin.Int:kotlin.fqn.class]Int|, |y|: |[kotlin.String:kotlin.fqn.class]String|) |->| |[kotlin.String:kotlin.fqn.class]String #>*/ = action
    val barRef/*<# : |[kotlin.reflect.KFunction1:kotlin.fqn.class]KFunction1|<|[kotlin.Boolean:kotlin.fqn.class]Boolean|, |[kotlin.Unit:kotlin.fqn.class]Unit|> #>*/ = ::bar
    val aFuncRef/*<# : |[kotlin.reflect.KFunction2:kotlin.fqn.class]KFunction2|<|[A:kotlin.fqn.class]A|, |[kotlin.String:kotlin.fqn.class]String|, |[kotlin.String:kotlin.fqn.class]String|> #>*/ = A::func
    val aExtRef/*<# : |[kotlin.reflect.KFunction2:kotlin.fqn.class]KFunction2|<|[A:kotlin.fqn.class]A|, |[kotlin.Int:kotlin.fqn.class]Int|, |[kotlin.Int:kotlin.fqn.class]Int|> #>*/ = A::extension
}
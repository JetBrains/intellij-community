// "Initialize with constructor parameter" "true"
// WITH_STDLIB
abstract class Form<T>(val name: String){
    var <caret>data: T?
        set(value){
            value?.let { processData(it) }
            field = data
        }

    abstract protected fun processData(data: T)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix
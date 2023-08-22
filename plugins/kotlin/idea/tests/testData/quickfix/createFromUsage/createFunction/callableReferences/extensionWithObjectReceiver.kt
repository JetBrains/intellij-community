// "Create extension function 'Foo.bar'" "true"
object Foo

val f: (String) -> Int = Foo::bar<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// "Create object 'Foo'" "true"
 class Test{
    fun doSth(){
        <caret>Foo::class.java
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
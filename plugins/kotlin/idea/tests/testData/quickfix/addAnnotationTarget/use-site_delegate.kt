// "Add annotation target" "true"
// WITH_STDLIB
@Target
annotation class DelegateAnn

<caret>@delegate:DelegateAnn
val foo by lazy { "" }
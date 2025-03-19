// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual open class <caret>A<T: A<T>> {
    actual class C<T>
    actual inner class B<F : C<C<C<A<T>>>>>
}
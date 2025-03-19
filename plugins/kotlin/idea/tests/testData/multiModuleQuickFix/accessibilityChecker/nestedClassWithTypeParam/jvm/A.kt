// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

class A<T> {
    actual class B<F : T>
    actual class C<F>
    actual class D<F : Any>
    actual class <caret>E<F : Any?>
    actual class G<F : Dwwwq>
}
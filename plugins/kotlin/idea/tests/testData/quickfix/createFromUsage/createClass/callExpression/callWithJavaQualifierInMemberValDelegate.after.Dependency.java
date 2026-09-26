import kotlin.properties.ReadOnlyProperty;
import org.jetbrains.annotations.NotNull;

class J {

    public static class Foo<T> implements ReadOnlyProperty<? super A<T>, ? extends B> {
        public Foo(T t, @NotNull String s) {
        }
    }
}
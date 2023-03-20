package typeAnnotations;

@FunctionalInterface
public interface SomeFunInterface<T, E> {
    void accept(T t, E e);
}

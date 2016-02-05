class LambdaUnfriendlyMethodOverload {


}
interface Application {
  <T> T <warning descr="Lambda unfriendly overload of method 'runWriteAction()'">runWriteAction</warning>(@<error descr="Cannot resolve symbol 'NotNull'">NotNull</error> Computable<T> computation);

  <T, E extends Throwable> T <warning descr="Lambda unfriendly overload of method 'runWriteAction()'">runWriteAction</warning>(@<error descr="Cannot resolve symbol 'NotNull'">NotNull</error> ThrowableComputable<T, E> computation) throws E;
}
interface Computable <T> {
  T compute();
}
interface ThrowableComputable<T, E extends Throwable> {
  T compute() throws E;
}
interface Adder {
  String add(Function<String, String> f);
  void add(Consumer<Integer> f);
}
interface Stream<T> {
  <V> V <warning descr="Lambda unfriendly overload of method 'map()'">map</warning>(Function<T, V> mappar);
  <V> V <warning descr="Lambda unfriendly overload of method 'map()'">map</warning>(IntFunction<V> intMapper);
}
class X {
  void a(IntPredicate p) {}
  void a (Function<String, String> f) {}

  void b() {
    a(z -> true);
  }
}
interface Function<T, R> {
  R apply(T t);
}
interface IntFunction<R> {
  R apply(int value);
}
interface Consumer<T> {
  void accept(T t);
}
interface IntPredicate {
  boolean test(int value);
}
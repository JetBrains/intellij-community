class LambdaUnfriendlyMethodOverload extends Super {

  @Override
  void a(Runnable r) {}
}
class Super {
  void a(Runnable r) {}
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
  <warning descr="Lambda unfriendly overload of constructor 'X()'">X</warning>(IntPredicate p) {}
  <warning descr="Lambda unfriendly overload of constructor 'X()'">X</warning>(Function<String, String> f) {}

  void <warning descr="Lambda unfriendly overload of method 'a()'">a</warning>(IntPredicate p) {}
  void <warning descr="Lambda unfriendly overload of method 'a()'">a</warning> (Function<String, String> f) {}

  void b() {
    <error descr="Ambiguous method call: both 'X.a(IntPredicate)' and 'X.a(Function<String, String>)' match">a</error>(z -> true);
  }

  void assertEquals(double expected, Runnable messageSupplier) {}
  void assertEquals(Object expected, Runnable messageSupplier) {}
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
class Generics<M, K> {
  void <warning descr="Lambda unfriendly overload of method 'm()'">m</warning>(IntFunction r, M l) {}
  void <warning descr="Lambda unfriendly overload of method 'm()'">m</warning>(Function f, K l) {}

}
class A {
  static void foo(Function f) {}
}

class B extends A {
  static void <warning descr="Lambda unfriendly overload of method 'foo()'">foo</warning>(IntFunction f) {}
}
class FinalClassWithTypeParamer<T<caret> extends A<String>> {

  T t;
}
final class A<T> {}
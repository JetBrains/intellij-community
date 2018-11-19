class FinalClassWithTypeParamer<T<caret> extends //c1 
  A<String>> {

  T t;
}
final class A<T> {}
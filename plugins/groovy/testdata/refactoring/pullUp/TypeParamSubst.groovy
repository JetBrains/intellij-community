public class Parent<S> {}

class Child<T> extends Parent<T> {
   T <caret>f;
}

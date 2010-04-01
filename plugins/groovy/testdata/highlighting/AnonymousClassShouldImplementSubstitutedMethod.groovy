class TestClass {}

print new Predicate<TestClass>() {
  boolean evaluate(TestClass test) {
    return test != null;
  }
};

interface Predicate<T> {
  boolean evaluate(T test)
}

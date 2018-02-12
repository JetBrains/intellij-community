import java.io.Closeable;

class DummyClass {

  public void doSomething() {
  }
}

class CloseableDummyClass extends DummyClass implements Closeable {

  @Override
  public void close() {
  }
}

class CallerClass {

  public void call(CloseableDummyClass closeableDummyClass) {
    try (closeableDummyClass) {
      closeableDummyClass.doSomething();
    }
  }
}
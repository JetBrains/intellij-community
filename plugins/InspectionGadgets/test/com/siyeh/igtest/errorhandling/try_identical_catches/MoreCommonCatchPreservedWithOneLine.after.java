import java.io.IOException;

class C {
  void foo() {
    try {
      bar();
    }
    catch (RuntimeException e) {
      System.out.println("1");
      throw e;
    }
    catch (<caret>Exception exception) {}
    catch (Throwable throwable) {
      System.out.println("1");
    }
  }

  void bar() throws IOException{}
}
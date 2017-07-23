package pkg;

public class TestVarArgCalls {
  public void doSmth() {
    printAll("Test");
    printAll("Test: %s", "abc");
    printAll("Test: %s - %s", "abc", "DEF");

    printComplex("Test");
    printComplex("Test: %[0]s", new String[] { "abc" });
    printComplex("Test: %[0]s - %[0]s", new String[] { "abc" }, new String[] { "DEF" });

    String.format("Test");
    String.format("Test: %d", 123);
    String.format("Test: %d - %s", 123, "DEF");

    Object[] data = { "Hello" };
    String.format("Test: %s", (Object) data);
    String.format("Test: %s", (Object[]) data);
  }

  public void printAll(String fmt, String... params) {
    System.out.println(String.format(fmt, (Object[]) params));
  }

  public void printComplex(String fmt, String[]... params) {
    System.out.println(String.format(fmt, (Object[]) params));
  }
}

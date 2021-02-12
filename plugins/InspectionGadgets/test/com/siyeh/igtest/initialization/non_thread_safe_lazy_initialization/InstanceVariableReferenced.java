public class InstanceVariableReferenced {

  private static Object example;
  private String s = "yes";

  public Object getInstance() {
    if (example == null) {
      <warning descr="Lazy initialization of 'static' field 'example' is not thread-safe"><caret>example</warning> = getString(s);
    }
    return example;
  }

  private static String getString(String s) {
    return new String(s);
  }
}
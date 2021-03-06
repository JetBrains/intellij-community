
import org.jetbrains.annotations.NonNls;

@NonNls
class MyTest {
  public static final String CONSTANT_1 = "One";
  public static final String CONSTANT_2 = "Two";

  void foo(String s) {}
  {
    foo("Three in method call");
  }
}
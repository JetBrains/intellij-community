import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nls;

class MyTest {
  void test() {
    StringBuilder sb1 = new StringBuilder();
    @Nls StringBuilder sb2 = new StringBuilder();
    @NonNls StringBuilder sb3 = new StringBuilder();
    
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">sb1.toString()</warning>);
    consume(sb2.toString());
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">sb3.toString()</warning>);
  }
  
  void consume(@Nls String s) {}
}
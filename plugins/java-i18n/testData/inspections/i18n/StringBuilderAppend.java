import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

class MyTest {
  native void consume(@Nls String s); 
  
  void test(@NonNls String s) {
    @Nls StringBuilder sb = new StringBuilder();
    sb.append(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>);
    sb.append(s);
    consume(sb.toString());

    @NonNls StringBuilder sb2 = new StringBuilder();
    sb2.append("foo");
    sb2.append(s);
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">sb2.toString()</warning>);
  }
}
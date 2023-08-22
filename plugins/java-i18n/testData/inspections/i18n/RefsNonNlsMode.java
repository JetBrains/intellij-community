import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

class MyTest {
  native @Nls String getNls();
  native String getNonNls();
  native void consume(@Nls String nls);
  native void consumeUnannotated(String nls);
  native void consumeNonNls(@NonNls String nls);
  
  void getFoo() {
    consume(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>);
    consume(getNls());
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">getNonNls()</warning>);
    consumeUnannotated(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>);
    consumeUnannotated(getNls());
    consumeUnannotated(getNonNls());
    consumeNonNls("foo");
    consumeNonNls(getNls());
    consumeNonNls(getNonNls());
  }
  
}
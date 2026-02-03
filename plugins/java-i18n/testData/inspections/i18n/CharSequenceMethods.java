import org.jetbrains.annotations.Nls;

class MyTest {
  native @Nls CharSequence getNls();
  native CharSequence getNonNls();
  native void consume(@Nls String nls);
  
  void getFoo() {
    consume(getNls().toString());
    consume(getNls().subSequence(1, 10).toString());
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">getNonNls()</warning>.toString());
    consume(<warning descr="Reference to non-localized string is used where localized string is expected">getNonNls()</warning>.subSequence(1, 10).toString());
  }
  
}
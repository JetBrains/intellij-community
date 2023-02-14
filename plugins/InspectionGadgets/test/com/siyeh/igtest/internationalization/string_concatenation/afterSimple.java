import org.jetbrains.annotations.NonNls;

// "Annotate parameter 'str' as '@NonNls'" "true"
class X {
  void test(@NonNls String str) {
    String result = "foo" + str;  
  }
}


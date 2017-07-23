import java.util.List;

class MyClass {


  String myStr;
  void f(List<String> l){
    l.stream().filter(this::test);
  }

    private boolean test(String s) {
        return s.startsWith(myStr);
    }
}
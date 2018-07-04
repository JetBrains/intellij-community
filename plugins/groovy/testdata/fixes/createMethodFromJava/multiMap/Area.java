import java.util.Map;

class BrokenCreateMethod {

  public void foo(Map<String, String> bar) {
    Map<String, String[]> multiBar = new A().<caret>toMulti(bar);
  }
}
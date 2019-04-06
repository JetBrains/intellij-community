import java.util.*;

public class NestedLambdaNameConflict {
  public void test(Map<String, String> m){
    for(String k : m.<caret>keySet()) {
      System.out.println(k);
      Foo foo = entry -> entry.getKey().equals(m.get(k));
    }
  }
  
  interface Foo {
    boolean doSmth(Map.Entry<String, String> e);
  }
}
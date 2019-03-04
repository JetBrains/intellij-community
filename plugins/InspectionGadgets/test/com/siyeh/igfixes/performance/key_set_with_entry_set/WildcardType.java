import java.util.*;

public class EntryIterationBug {
  public void putAll(Map<? extends SomeClass, ? extends List<SomeClass>> m){
    for(SomeClass wrapper : m.k<caret>eySet()){
      for(SomeClass wrapper2 : m.get(wrapper)){
        put(wrapper2);
      }
    }
  }
  
  void put(SomeClass s) {}
  
  class SomeClass {}
}
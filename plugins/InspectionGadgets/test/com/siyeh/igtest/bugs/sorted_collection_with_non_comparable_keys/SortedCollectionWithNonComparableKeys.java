import java.util.*;
import java.util.concurrent.*;

class A {}

class B {
  void m() {
    Set<A> set = <warning descr="Construction of sorted collection with non-comparable elements">new TreeSet<>()</warning>;
  }

  void object() {
    Set<Object> obj = new TreeSet<>(); // do not report objects
    Set raw = new TreeSet();
  }

  void map() {
    Map<A, String> map = <warning descr="Construction of sorted collection with non-comparable elements">new TreeMap<>()</warning>;
    Map<String, A> map2 = new TreeMap<>();
  }

  void concurrent() {
    Set<A> set = new ConcurrentSkipListSet<>();
    Map<A, String> map = new ConcurrentSkipListMap<>();
    Map<String, A> map2 = new ConcurrentSkipListMap<>();
  }

  <T> Set<T> createSet() {
    return new TreeSet<>();
  }
}
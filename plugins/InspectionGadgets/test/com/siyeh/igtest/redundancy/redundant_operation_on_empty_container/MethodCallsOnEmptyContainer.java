import java.util.*;

class Calls {
  void testArray() {
    String[] data = new String[0];
    Arrays.sort(<warning descr="Array 'data' is always empty">data</warning>);
    Arrays.fill(<warning descr="Array 'data' is always empty">data</warning>, "foo");
    Arrays.stream(<warning descr="Array 'data' is always empty">data</warning>).forEach(System.out::println);
  }
  
  void testCollection() {
    List<String> list = Collections.emptyList();
    <warning descr="Collection 'list' is always empty">list</warning>.clear();
    <warning descr="Collection 'list' is always empty">list</warning>.remove("foo");
    <warning descr="Collection 'list' is always empty">list</warning>.replaceAll(String::trim);
    <warning descr="Collection 'list' is always empty">list</warning>.forEach(System.out::println);
    <warning descr="Collection 'list' is always empty">list</warning>.iterator();
    <warning descr="Collection 'list' is always empty">list</warning>.spliterator();
    <warning descr="Collection 'list' is always empty">list</warning>.sort(null);
  }
  
  void testMap() {
    Map<String, String> map = Collections.emptyMap();
    <warning descr="Map 'map' is always empty">map</warning>.get("foo");
    <warning descr="Map 'map' is always empty">map</warning>.remove("foo");
    <warning descr="Map 'map' is always empty">map</warning>.remove("foo", "bar");
    <warning descr="Map 'map' is always empty">map</warning>.replace("foo", "bar");
    <warning descr="Map 'map' is always empty">map</warning>.replace("foo", "bar", "baz");
    <warning descr="Map 'map' is always empty">map</warning>.forEach((k, v) -> { });
  }
  
  
}
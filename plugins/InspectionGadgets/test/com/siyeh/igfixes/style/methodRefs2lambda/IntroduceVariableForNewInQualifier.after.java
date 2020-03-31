import java.util.*;

class Test {
  {
    List<String> strings = Arrays.asList("a", "a");
      HashSet<Object> objects = new HashSet<>();
      System.out.println(strings.stream().allMatch(e -><caret> objects.add(e)));
  }
}
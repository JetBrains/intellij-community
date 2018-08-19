import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class EntryIterationBug {
  private final Map<String, Double> map = new HashMap<>();

  public void merge(EntryIterationBug other) {
    for (Map.Entry<String, Double> stringDoubleEntry : other.map.entrySet()) {
      if (map.containsKey(stringDoubleEntry.getKey())) {
        map.put(stringDoubleEntry.getKey(), map.get(stringDoubleEntry.getKey()) + stringDoubleEntry.getValue());
      } else {
        map.put(stringDoubleEntry.getKey(), stringDoubleEntry.getValue());
      }
    }
  }

  public static void main(String[] args) {
    EntryIterationBug first = new EntryIterationBug();
    first.map.put("x", 50.0);

    EntryIterationBug second = new EntryIterationBug();
    second.map.put("x", 10.0);

    first.merge(second);
    System.out.println("Expect 60.0, result is: " + first.map.get("x"));
  }
}
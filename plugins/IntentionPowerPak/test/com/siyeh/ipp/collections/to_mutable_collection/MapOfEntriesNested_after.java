import java.util.*;
import static java.util.Map.entry;

class MapOfEntriesNested {
  Map<Integer, String> BINDINGS;

    {
        BINDINGS = new HashMap<>();
        BINDINGS.put(1, "one");
        BINDINGS.put(2, "two");
    }
}
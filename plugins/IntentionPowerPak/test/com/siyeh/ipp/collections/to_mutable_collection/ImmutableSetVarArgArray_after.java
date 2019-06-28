import java.util.*;
import com.google.common.collect.*;

class Test {

  Set<String> test(String[] rest) {
      Set<String> strings = new HashSet<>(ImmutableSet.of("1", "2", "3", "4", "5", "6", rest));
      return strings;
  }
}
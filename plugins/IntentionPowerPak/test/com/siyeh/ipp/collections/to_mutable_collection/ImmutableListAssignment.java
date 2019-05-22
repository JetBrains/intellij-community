import java.util.*;
import com.google.common.collect.*;

class Test {

  void test() {
    ImmutableList<String> set = ImmutableList.of<caret>("foo", "bar");
  }

}
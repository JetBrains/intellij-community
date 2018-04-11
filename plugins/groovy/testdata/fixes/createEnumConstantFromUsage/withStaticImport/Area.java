import java.util.Arrays;
import java.util.List;

import static E.*;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  public static void main(String[] args) {

    new AutomaticTypeInference(Arrays.asList(AAA, CC<caret>C));

  }
}
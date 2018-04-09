import java.util.*;

class AutomaticTypeInference {

  AutomaticTypeInference(List<E> gs) {
  }

  public static void main(String[] args) {

    new AutomaticTypeInference(Arrays.asList(E.AAA, E.C<caret>CC));

  }
}
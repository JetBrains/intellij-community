import com.intellij.openapi.util.Pair;

class UseCoupleTypeInMethodParameter {
  void takePair(<warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String></warning> pair) {
    // do nothing
  }
}

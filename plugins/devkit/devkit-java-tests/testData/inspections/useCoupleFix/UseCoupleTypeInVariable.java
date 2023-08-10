import com.intellij.openapi.util.Pair;

class UseCoupleTypeInVariable {
  void any() {
    <warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String></warning> any = null;
  }
}

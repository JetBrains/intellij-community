import com.intellij.openapi.util.Pair;

class UseCoupleTypeInConstant {
  private static final <warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String></warning> ANY = null;
}

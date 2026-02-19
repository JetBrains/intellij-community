import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.MyTokenType;

class MyTokens {
  private MyTokenType <warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">customToken</warning> = new MyTokenType("CUSTOM");

  private static final MyTokenType STATIC_CUSTOM = new MyTokenType("STATIC_CUSTOM");
}

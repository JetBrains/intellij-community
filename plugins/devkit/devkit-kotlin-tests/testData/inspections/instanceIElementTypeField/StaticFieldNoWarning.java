import com.intellij.psi.tree.IElementType;

class MyTokens {
  private static final IElementType STATIC_TOKEN = new IElementType("STATIC");

  private IElementType uninitializedToken;

  private String stringField = "test";
}

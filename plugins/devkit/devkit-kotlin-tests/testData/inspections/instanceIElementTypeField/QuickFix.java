import com.intellij.psi.tree.IElementType;

class MyTokens {
  private IElementType <<caret>warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">myToken</warning> = new IElementType("MY_TOKEN");
}

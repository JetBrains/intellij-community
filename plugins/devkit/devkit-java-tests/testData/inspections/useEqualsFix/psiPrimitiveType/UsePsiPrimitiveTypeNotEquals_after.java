import com.intellij.psi.PsiPrimitiveType;

class UsePluginIdEquals {

  void any() {
    PsiPrimitiveType type1 = PsiPrimitiveType.BYTE;
    PsiPrimitiveType type2 = PsiPrimitiveType.DOUBLE;

    boolean result = !type1.equals(type2);
  }

}

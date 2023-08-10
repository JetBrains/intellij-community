import com.intellij.psi.PsiPrimitiveType;

class UsePluginIdEquals {

  void any() {
    PsiPrimitiveType type1 = PsiPrimitiveType.BYTE;
    PsiPrimitiveType type2 = PsiPrimitiveType.DOUBLE;

    boolean result = <warning descr="'PsiPrimitiveType' instances should be compared for equality, not identity">type1<caret> != type2</warning>;
  }

}

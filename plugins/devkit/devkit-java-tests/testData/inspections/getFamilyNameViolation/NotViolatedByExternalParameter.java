import com.intellij.codeInspection.QuickFix;

class A {

  QuickFix getFix(final String someParameter) {
    return new QuickFix() {
      public String getName() {
        return "some name";
      };

      public String getFamilyName() {
        return someParameter + "123";
      };
    };
  }

}

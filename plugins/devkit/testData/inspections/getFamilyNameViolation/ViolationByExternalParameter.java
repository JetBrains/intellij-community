import com.intellij.codeInspection.QuickFix;

class A {

  QuickFix getFix(final String someParameter) {
    return new QuickFix() {
      public String getName() {
        return "some name";
      };

      public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
        return someParameter + "123";
      };
    };
  }

}

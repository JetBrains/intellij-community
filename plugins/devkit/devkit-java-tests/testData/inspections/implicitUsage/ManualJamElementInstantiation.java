public class ManualJamElementInstantiation implements com.intellij.jam.JamElement {

  protected void foo() {};

  public static void main(String[] args) {

    ManualJamElementInstantiation elementInstantiation = new ManualJamElementInstantiation() {
      @java.lang.Override
      protected void foo() {
        super.foo();
      }
    };
  }
}
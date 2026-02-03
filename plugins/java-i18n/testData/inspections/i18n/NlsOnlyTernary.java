import org.jetbrains.annotations.Nls;

class Foo {
  @Nls String foo() {
    return null;
  }

  @Nls String bar(String packageName, String extractedSuperName) {
    return packageName.equals(extractedSuperName) ? foo() : <warning descr="Reference to non-localized string is used where localized string is expected">packageName</warning>;
  }
}
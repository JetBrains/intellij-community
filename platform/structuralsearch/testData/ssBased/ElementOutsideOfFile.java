import org.intellij.lang.annotations.Language;

public class ElementOutsideOfFile {

  void <warning descr="all methods of the class within hierarchy">x</warning>() {}

  @Language("JAVA")
  String java = "class X {" +
                "  void <warning descr="all methods of the class within hierarchy">x</warning>() {}" +
                "}";
}
public class GrDefFieldsArePrivateInJavaCode {
  public static void main(String[] args) {
    System.out.println(new X().<error descr="'x' has private access in 'X'">x</error>);
  }
}
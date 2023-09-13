import java.io.IOException;

class AAA {
  public static void main(String[] args) {
    try {
      System.out.println(System.in.read());
    } c<caret>atch (IOException ex) {

    }
  }
}
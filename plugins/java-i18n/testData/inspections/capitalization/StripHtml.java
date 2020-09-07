import org.jetbrains.annotations.*;

class X {
  void test(@Nls(capitalization = Nls.Capitalization.Title) String title) {

  }

  void test2(@Nls(capitalization = Nls.Capitalization.Sentence) String title) {

  }

  void main(int x) {
    test("<html>Hello World!</html>");
    test2(<warning descr="String '<html>Hello World!</html>' is not properly capitalized. It should have sentence capitalization">"<html>Hello World!</html>"</warning>);
    test(<warning descr="String '<html>Hello world!</html>' is not properly capitalized. It should have title capitalization">"<html>Hello world!</html>"</warning>);
    test2("<html>Hello world!</html>");
  }
}
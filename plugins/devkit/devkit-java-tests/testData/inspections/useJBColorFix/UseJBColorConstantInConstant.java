import java.awt.Color;

class UseJBColorConstructor {

  private static final Color COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Col<caret>or.BLUE</warning>;

}

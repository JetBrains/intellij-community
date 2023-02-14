import java.awt.Color;

class UseJBColorConstructorInConstant {

  private static final Color COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">new Co<caret>lor(1, 2, 3)</warning>;

}

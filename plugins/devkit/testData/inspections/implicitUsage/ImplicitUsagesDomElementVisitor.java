import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;

public class ImplicitUsagesDomElementVisitor implements DomElementVisitor {

  public static void main(String[] args) {} // suppress class unused

  interface MyDom extends DomElement {}

  public void visitMyDom(MyDom myDom) { myDom = null; }
  public void visit(MyDom myDom) { myDom = null; }

  // invalid ===========
  public String <warning descr="Method 'nonVoidReturnTypeMethod(ImplicitUsagesDomElementVisitor.MyDom)' is never used">nonVoidReturnTypeMethod</warning>(MyDom myDom) { myDom = null; return null; }
  public void <warning descr="Method 'doesNotStartWithVisit(ImplicitUsagesDomElementVisitor.MyDom)' is never used">doesNotStartWithVisit</warning>(MyDom myDom) { myDom = null; }
  public void <warning descr="Method 'nonDomParameter(int)' is never used">nonDomParameter</warning>(int i) { i = 0; }
  private void <warning descr="Private method 'visitMyDomMoreThanOneParam(ImplicitUsagesDomElementVisitor.MyDom, int)' is never used">visitMyDomMoreThanOneParam</warning>(MyDom myDom, int index) { myDom = null; index = 0; }
  private void <warning descr="Private method 'visitMyDomPrivate(ImplicitUsagesDomElementVisitor.MyDom)' is never used">visitMyDomPrivate</warning>(MyDom myDom) { myDom = null; }
  public static void <warning descr="Method 'visitMyDomStatic(ImplicitUsagesDomElementVisitor.MyDom)' is never used">visitMyDomStatic</warning>(MyDom myDom) { myDom = null; }


  public static class NonDomElementVisitorClass {

    public static void main(String[] args) {} // suppress class unused

    public void <warning descr="Method 'visitMyDom(ImplicitUsagesDomElementVisitor.MyDom)' is never used">visitMyDom</warning>(MyDom myDom) { myDom = null; }

  }

}
import java.util.List;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;

public abstract class ImplicitUsagesDomElement implements DomElement {

  public abstract GenericAttributeValue<String> getStringAttribute();
  public abstract ImplicitUsagesDomElement getDomElement();

  public abstract List<ImplicitUsagesDomElement> getDoms();

  public abstract ImplicitUsagesDomElement addDom();
  public abstract ImplicitUsagesDomElement addDomIndexed(int index);


  // invalid ===========

  private GenericAttributeValue<String> <warning descr="Private method 'getStringAttributePrivate()' is never used">getStringAttributePrivate</warning>() { return null; };
  public static GenericAttributeValue<String> <warning descr="Method 'getStringAttributeStatic()' is never used">getStringAttributeStatic</warning>() { return null; };

  public String <warning descr="Method 'getNonDomString()' is never used">getNonDomString</warning>() { return null; };
  public int <warning descr="Method 'getNonDomPrimitiveType()' is never used">getNonDomPrimitiveType</warning>() { return 0; }

  private int <warning descr="Private method 'normalPrivateMethod()' is never used">normalPrivateMethod</warning>() { return 0; }

  public List<String> <warning descr="Method 'getNonDomList()' is never used">getNonDomList</warning>() { return null; }


  public abstract ImplicitUsagesDomElement <warning descr="Method 'addDomMoreThanOneParam(int, int)' is never used">addDomMoreThanOneParam</warning>(int i, int j);
  public String <warning descr="Method 'addNonDom()' is never used">addNonDom</warning>() { return null; }

}
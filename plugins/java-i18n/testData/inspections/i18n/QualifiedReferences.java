package pkg;

import org.jetbrains.annotations.Nls;

class QualifiedReferences {
  public static final String CONST = <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>;
  public final String myField = <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>;
  public static String staticFn() { return CONST; }
  public String nonStaticFn() { return myField; }
  
  public void take(@Nls String s) {}
  
  public void test(QualifiedReferences instance) {
    take(<warning descr="Reference to non-localized string is used where localized string is expected">QualifiedReferences.CONST</warning>);
    take(<warning descr="Reference to non-localized string is used where localized string is expected">QualifiedReferences.staticFn()</warning>);
    take(<warning descr="Reference to non-localized string is used where localized string is expected">pkg.QualifiedReferences.CONST</warning>);
    take(<warning descr="Reference to non-localized string is used where localized string is expected">pkg.QualifiedReferences.staticFn()</warning>);
    take(<warning descr="Reference to non-localized string is used where localized string is expected">instance.myField</warning>);
    take(<warning descr="Reference to non-localized string is used where localized string is expected">instance.nonStaticFn()</warning>);
  }
}

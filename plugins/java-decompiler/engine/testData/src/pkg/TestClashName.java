package pkg;

/*
 * The names SharedName[123] are shared between variables in local|parent class and class names.
 * Where approprate, the classes have to be referenced by fully qualified names
 */

class SharedName1 {
  static int f = 0;

  static int getF() {
    return f;
  }
}

class SharedName2 {
  static int f = 0;
}

class SharedName3 {
  static int f = 0;
}

class NonSharedName {
  static int f = 0;

  static int getF() {
    return f;
  }
}

@interface SharedName4 {
}

class SharedName5<T>{
}

class TestClashNameParent {

}

interface TestClashNameIface {
  public void f();
}
// *** Legend for first sentence in comments below:
// (+) or (-) indicate whether 'pkg' prefix is or is not required when referencing *SharedName*.
//  The sign is optionally followed by decompiler class name that generates the line that is being commented
// in a call of getShortName()

@SharedName4 // (-)AnnotationExprent. While a variable named SharedName4 does exist in the annotated class,
             // lookup process for annotation names never includes variable names.
public class TestClashName extends ext.TestClashNameParent implements /*pkg.*/TestClashNameIface { // (+)(-)TextUtil
  int TestClashNameParent = 0;
  int TestClashNameIface = 0;
  int SharedName1 = 0;
  int SharedName4 = 0;
  int SharedName5 = 0;

  int i = pkg.SharedName1.f;     // (+)FieldExprent. SharedName1 class name is shadowed by a variable in this class
  int j = NonSharedName.f;       // (-)FieldExprent. The NonSharedName is not used for other objects in the current scope
  int k = SharedName2.f;         // (-)FieldExprent. SharedName2 variable is not the current scope
  int l = pkg.SharedName3.f;     // (+)FieldExprent. SharedName3 class name is shadowed by a variable in parent class
  int m = pkg.SharedName1.getF();// (+)InvocationExprent.  SharedName1 class name is shadowed by a variable in this class
  int n = NonSharedName.getF();  // (-)InvocationExprent. The NonSharedName is not used for other objects in the current scope
  SharedName1 p = null;          // (-)ExprProcessor. While a variable named SharedName1 in current scope does exist,
                                 // namespace in type declaration does not include variable names in a scope
  SharedName5<SharedName1> q = null;//(-)(-)GenericMain (both names).  While a variable named SharedName1 does exist in current scope,
                                  // lookup for generic parameters never includes variable names

  @SharedName4 // (-)AnnotationExprent. While a variable named SharedName4 does exist in current scope,
               // lookup process for annotation names never includes variable names.
  public int m() {
    int SharedName2 = i;
    pkg.SharedName1.f = j;       // (+)FieldExprent. SharedName1 class name is shadowed by a variable in this class
    int x = pkg.SharedName2.f;   // (+)FieldExprent. SharedName2 class name is shadowed by a variable in this method
    NonSharedName.f = k;         // (-)ExprProcessor. The NonSharedName is not used for other objects in the current scope
    int y = NonSharedName.f;     // (-)FieldExprent. The NonSharedName is not used for other objects in the current scope
    return SharedName2 + x + y;
  }

  @Override
  public void f() {}
}

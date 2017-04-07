package pkg;

/**
 * Names SharedName[123] are shared between variables in local|parent class and class names.
 * Where approprate, the classes have to be referenced by fully qualified names.
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

class TestClashNameParent {
  int SharedName3 = 0;
}

public class TestClashName extends TestClashNameParent {
  int SharedName1 = 0;
  int i = pkg.SharedName1.f;
  int j = NonSharedName.f;
  int k = SharedName2.f;
  int l = pkg.SharedName3.f;
  int m = pkg.SharedName1.getF();
  int n = NonSharedName.getF();

  public int m() {
    int SharedName2 = i;
    pkg.SharedName1.f = j;
    int x = pkg.SharedName2.f;
    NonSharedName.f = k;
    int y = NonSharedName.f;
    return SharedName2 + x + y;
  }
}

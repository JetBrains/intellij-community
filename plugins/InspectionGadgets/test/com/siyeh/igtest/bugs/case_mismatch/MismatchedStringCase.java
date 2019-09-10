import java.util.Locale;

class X {
  void test(String s1, String s2) {
    if(s1.toLowerCase().<warning descr="Method 'startsWith()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">startsWith</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Method 'startsWith()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">startsWith</warning>("FOO", 1)) {}
    if(s1.toLowerCase().<warning descr="Method 'equals()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">equals</warning>("FOO")) {}
    if(s1.toLowerCase().equalsIgnoreCase("FOO")) {} // ok
    if(s1.toLowerCase().<warning descr="Method 'endsWith()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">endsWith</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Method 'contains()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">contains</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Method 'indexOf()' always returns -1: the argument contains an uppercase symbol while the qualifier is lowercase-only">indexOf</warning>("FOO") == -1) {}
    if(s1.toUpperCase(Locale.ENGLISH).<warning descr="Method 'startsWith()' always returns false: the argument contains a lowercase symbol while the qualifier is uppercase-only">startsWith</warning>("AAAAAAaAAA")) {}
    String s3 = s1.toLowerCase();
    if(s3.equals("")) {}
    if(s3.<warning descr="Method 'equals()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">equals</warning>("X")) {}
    if(s3.<warning descr="Method 'equals()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">equals</warning>("Hello " + s2)) {}
    if(s3.equals(s2.toUpperCase())) {} // strange but possible if both contain no letters
    if(s3.equals(s2.toUpperCase()+"!")) {} // also possible
    if(s3.<warning descr="Method 'equals()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">equals</warning>(s2.toUpperCase()+"!"+"X")) {}

  }

  void reassignParameter(String s) {
    s = s.toLowerCase();
    if (s.<warning descr="Method 'equals()' always returns false: the argument contains an uppercase symbol while the qualifier is lowercase-only">equals</warning>("Yes")) {

    }
  }
}
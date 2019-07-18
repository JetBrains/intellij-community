import java.util.Locale;

class X {
  void test(String s1, String s2) {
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">startsWith</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">startsWith</warning>("FOO", 1)) {}
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">equals</warning>("FOO")) {}
    if(s1.toLowerCase().equalsIgnoreCase("FOO")) {} // ok
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">endsWith</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">contains</warning>("FOO")) {}
    if(s1.toLowerCase().<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">indexOf</warning>("FOO") == -1) {}
    if(s1.toUpperCase(Locale.ENGLISH).<warning descr="Incompatible case: the string on the left has no lower case characters while the string on the right has">startsWith</warning>("AAAAAAaAAA")) {}
    String s3 = s1.toLowerCase();
    if(s3.equals("")) {}
    if(s3.<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">equals</warning>("X")) {}
    if(s3.<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">equals</warning>("Hello " + s2)) {}
    if(s3.equals(s2.toUpperCase())) {} // strange but possible if both contain no letters
    if(s3.equals(s2.toUpperCase()+"!")) {} // also possible
    if(s3.<warning descr="Incompatible case: the string on the left has no upper case characters while the string on the right has">equals</warning>(s2.toUpperCase()+"!"+"X")) {}

  }
}
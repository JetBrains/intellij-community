import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Target([PARAMETER, FIELD, METHOD, ANNOTATION_TYPE, LOCAL_VARIABLE])
@Retention(RUNTIME)
@interface JSR308 { }

class JSR308BaseClass<T> {}
interface JSR308Interface1<T> {}
interface JSR308Interface2<T extends @<error descr="'@JSR308' not applicable to type use">JSR308</error> CharSequence> {}

class JSR308Class extends @<error descr="'@JSR308' not applicable to type use">JSR308</error> JSR308BaseClass<@<error descr="'@JSR308' not applicable to type use">JSR308</error> List> implements @<error descr="'@JSR308' not applicable to type use">JSR308</error> JSR308Interface1<@<error descr="'@JSR308' not applicable to type use">JSR308</error> String>, @<error descr="'@JSR308' not applicable to type use">JSR308</error> JSR308Interface2<@<error descr="'@JSR308' not applicable to type use">JSR308</error> String> {
  @JSR308 private  String name;

  @JSR308 List<@<error descr="'@JSR308' not applicable to type use">JSR308</error> String> test(@JSR308 List<@<error descr="'@JSR308' not applicable to type use">JSR308</error> ? extends @<error descr="'@JSR308' not applicable to type use">JSR308</error> Object> list) throws @<error descr="'@JSR308' not applicable to type use">JSR308</error> IOException, @<error descr="'@JSR308' not applicable to type use">JSR308</error> java.sql.SQLException {
    @JSR308 List<@<error descr="'@JSR308' not applicable to type use">JSR308</error> String> localVar = new @<error descr="'@JSR308' not applicable to type use">JSR308</error> ArrayList<@<error descr="'@JSR308' not applicable to type use">JSR308</error> String>();

    try {
      for (e in list) {
        String t = (@<error descr="'@JSR308' not applicable to type use">JSR308</error> String) e;
        localVar.add(t);
      }
    } catch (@JSR308 Exception e) {
    }

    String @<error descr="'@JSR308' not applicable to type use">JSR308</error> []  strs = new String @JSR308 [] { 'a' }
    String @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] strs2 = new String @JSR308 [] @JSR308 [] { new String[] {'a', 'b'} }
    String @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] strs3 = new String @JSR308 [1] @JSR308 [2] @JSR308 []
    String @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] @<error descr="'@JSR308' not applicable to type use">JSR308</error> [] strs4 = new String @JSR308 [1] @JSR308 [2] @JSR308 [] @JSR308 []

    localVar.add(strs[0])
    localVar.add(strs2[0][1])
    assert null != strs3
    assert null != strs4

    return localVar
  }
}

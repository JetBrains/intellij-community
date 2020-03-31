import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.*
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Target([PARAMETER, FIELD, METHOD, ANNOTATION_TYPE, TYPE_USE, LOCAL_VARIABLE])
@Retention(RUNTIME)
@interface JSR308 { }

class JSR308BaseClass<T> {}
interface JSR308Interface1<T> {}
interface JSR308Interface2<T extends @JSR308 CharSequence> {}

class JSR308Class extends @JSR308 JSR308BaseClass<@JSR308 List> implements @JSR308 JSR308Interface1<@JSR308 String>, @JSR308 JSR308Interface2<@JSR308 String> {
  @JSR308 private  String name;

  @JSR308 List<@JSR308 String> test(@JSR308 List<@JSR308 ? extends @JSR308 Object> list) throws @JSR308 IOException, @JSR308 java.sql.SQLException {
    @JSR308 List<@JSR308 String> localVar = new @JSR308 ArrayList<@JSR308 String>();

    try {
      for (e in list) {
        String t = (@JSR308 String) e;
        localVar.add(t);
      }
    } catch (@JSR308 Exception e) {
    }

    String @JSR308 []  strs = new String @JSR308 [] { 'a' }
    String @JSR308 [] @JSR308 [] strs2 = new String @JSR308 [] @JSR308 [] { new String[] {'a', 'b'} }
    String @JSR308 [] @JSR308 [] @JSR308 [] strs3 = new String @JSR308 [1] @JSR308 [2] @JSR308 []
    String @JSR308 [] @JSR308 [] @JSR308 [] @JSR308 [] strs4 = new String @JSR308 [1] @JSR308 [2] @JSR308 [] @JSR308 []

    localVar.add(strs[0])
    localVar.add(strs2[0][1])
    assert null != strs3
    assert null != strs4

    return localVar
  }
}

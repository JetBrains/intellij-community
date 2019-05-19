import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class Cl {
  private int a
  String b
  Object t
}

@NamedVariant
String foo(@NamedParam int shade, Thread t, @NamedDelegate Cl <error descr="Duplicate named parameter 't' occurs in parameters: 't', 'e'">e</error>) {
  null
}

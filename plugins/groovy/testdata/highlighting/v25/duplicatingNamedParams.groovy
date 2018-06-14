import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class Cl {
  private int a
  String b
  Object t
}

@NamedVariant
String foo(@NamedParam int shade, Thread t, <error descr="Duplicating named parameter 't' occurs in parameters: 't', 'e'">@NamedDelegate Cl e</error>) {
  null
}

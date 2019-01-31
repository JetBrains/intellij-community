import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class E {
  private int a
  void setS(def _s){}
}

@NamedVariant
String foo(@NamedParam int shade, def s, @NamedDelegate E <error descr="Duplicate named parameter 's' occurs in parameters: 's', 'e'">e</error>) {
  null
}
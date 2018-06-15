import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class E {
  private int a
  byte s
}

@NamedVariant
String foo(@NamedParam int shade, @NamedDelegate E e1, @NamedDelegate E <error descr="Duplicate named parameter 's' occurs in parameters: 'e1', 'e2'">e2</error>) {
  null
}
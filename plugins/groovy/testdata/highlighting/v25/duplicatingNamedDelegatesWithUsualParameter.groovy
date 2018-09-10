import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class E {
  private int a
  byte s
}

@NamedVariant
String foo(@NamedParam int shade, def s, @NamedDelegate E <error descr="Duplicate named parameter 's' occurs in parameters: 's', 'e1', 'e2'">e1</error>, @NamedDelegate E <error descr="Duplicate named parameter 's' occurs in parameters: 's', 'e1', 'e2'">e2</error>) {
  null
}


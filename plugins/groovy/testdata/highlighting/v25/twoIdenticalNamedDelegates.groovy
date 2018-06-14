import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant


class E {
  private int a
  byte s
}

@NamedVariant
String foo(@NamedParam int shade, @NamedDelegate E e1, <error descr="Duplicating named parameter 's' occurs in parameters: 'e1', 'e2'">@NamedDelegate E e2</error>) {
  null
}
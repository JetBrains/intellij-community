import groovy.transform.CompileStatic
import groovy.transform.NamedDelegate
import groovy.transform.NamedParam
import groovy.transform.NamedVariant

class E {
}

@CompileStatic
@NamedVariant
def m(@NamedParam String a, int b, @NamedDelegate E e){
  m<error descr="'m' in 'namedDelegateWithoutProperties' cannot be applied to '(java.lang.Integer, E)'">(1, new E())</error>
}
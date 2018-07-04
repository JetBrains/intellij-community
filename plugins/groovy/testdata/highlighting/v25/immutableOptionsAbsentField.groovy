import groovy.transform.Immutable
import groovy.transform.ImmutableOptions

@ImmutableOptions(knownImmutables = [
  'existedField',
  <error descr="Property 'absentField' does not exist">'absentField'</error>,
  <error descr="Property 'inheritedField' does not exist">'inheritedField'</error>,
  <error descr="Property 'staticField' does not exist">'staticField'</error>,
  <error descr="Property 'privateField' does not exist">'privateField'</error>,
  <error descr="Property 'publicField' does not exist">'publicField'</error>
])
@Immutable
class A extends B {
  Object existedField
  static Object staticField
  private Object privateField
  public Object publicField
}

class B {
  Object inheritedField
}
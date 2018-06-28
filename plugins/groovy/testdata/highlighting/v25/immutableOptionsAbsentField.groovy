import groovy.transform.ImmutableOptions

@ImmutableOptions(knownImmutables = [<error descr="Field 'absentField' does not exist">'absentField'</error>, 'existedField'])
class A{
  Object existedField
}
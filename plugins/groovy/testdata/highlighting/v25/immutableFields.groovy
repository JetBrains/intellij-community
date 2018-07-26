import groovy.transform.Immutable
import groovy.transform.ImmutableOptions
import groovy.transform.KnownImmutable

@ImmutableOptions(knownImmutableClasses = [Thread], knownImmutables = ['m', 'h2'])
@Immutable
class A {
  int a
  private Iterator <error descr="Field 'b' should have immutable type or be declared so with @ImmutableOptions">b</error>
  private static Iterator b2
  Iterator <error descr="Field 'c' should have immutable type or be declared so with @ImmutableOptions">c</error>
  String d
  E e
  URI f
  Thread g
  MuttableClass <error descr="Field 'h' should have immutable type or be declared so with @ImmutableOptions">h</error>
  MuttableClass h2
  ImmuttableClass i
  Object[] j = new Object[1]
  List<Object> k = new ArrayList<>()
  Set l
  Iterator m
  Map n = new HashMap<>()
  Closure<Object> o
}

class MuttableClass {
  int a
}

@KnownImmutable
class ImmuttableClass {
  int a
}

enum  E {}
abstract class X {
  <error descr="variable cannot have modifier 'abstract'">abstract def</error> foo<error descr="';', '}' or new line expected"><</error><error descr="';', '}' or new line expected">c</error>aret<error descr="';', '}' or new line expected">></error><error descr="';', '}' or new line expected">(</error><error descr="';', '}' or new line expected">S</error>tring s,<error descr="Identifier expected"> </error>int a<error descr="';', '}' or new line expected">)</error>

    def foo(String s) {
        return foo(s, 5);
    }
}

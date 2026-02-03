abstract class X {
  abstract def foo<<error descr="Cannot resolve symbol 'caret'">caret</error>><error descr="Identifier, string literal or '(' expected">(</error><error descr="';', '}' or new line expected">S</error>tring s,<error descr="Identifier expected"> </error>int a<error descr="';', '}' or new line expected">)</error>

    def foo(String s) {
        return foo(s, 5);
    }
}

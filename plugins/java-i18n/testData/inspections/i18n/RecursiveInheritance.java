<error descr="Cyclic inheritance involving 'A'">class A extends C</error>{
    String foo(String p){ return <warning descr="Hard coded string literal: \"text\"">"text"</warning>;}
}

<error descr="Cyclic inheritance involving 'A'">class B extends A</error>{
    String foo(String p){ return <warning descr="Hard coded string literal: \"text\"">"text"</warning>;}
}

<error descr="Cyclic inheritance involving 'C'">class C extends A</error>{
    String foo(String p){
      foo<error descr="Ambiguous method call: both 'C.foo(String)' and 'A.foo(String)' match">(<warning descr="Hard coded string literal: \"text\"">"text"</warning>)</error>;
      return <warning descr="Hard coded string literal: \"text\"">"text"</warning>;
    }
}
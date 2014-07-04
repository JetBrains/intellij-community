class A extends C{
    String foo(String p){ return "text";}
}

class B extends A{
    String foo(String p){ return "text";}
}

class C extends A{
    String foo(String p){
      foo("text");
      return "text";
    }
}
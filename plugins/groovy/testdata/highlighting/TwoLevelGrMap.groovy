def foo(Map<String, Map<String, Object>> m) {}

foo(yyy:[b:true, c:'a'], xxx:[b:true])
foo<warning descr="'foo' in 'TwoLevelGrMap' cannot be applied to '(['xxx':['b':java.lang.Boolean], java.lang.Integer:['b':java.lang.Boolean]])'">((2):[b:true], xxx:[b:true])</warning>

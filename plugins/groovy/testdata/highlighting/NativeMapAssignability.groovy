def foo(Map<String, Map<String, Object>> m) {}

foo(a:[b:'c'])
foo<warning descr="'foo' in 'NativeMapAssignability' cannot be applied to '(['a':java.lang.String])'">(a:'b')</warning>
foo<warning descr="'foo' in 'NativeMapAssignability' cannot be applied to '(['a':java.util.Map<java.lang.Integer, java.lang.Integer>])'">(a:[(2):4])</warning>
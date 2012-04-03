enum My {
  foo, bar
}

My var = 'foo'
var = <warning descr="Cannot find enum constant 'fail' in enum 'My'">'fail'</warning>

var = <weak_warning descr="Cannot assign string to enum 'My'">"fo"+"o"</weak_warning>
var=<weak_warning descr="Cannot assign string to enum 'My'">"fo${'o'}"</weak_warning>

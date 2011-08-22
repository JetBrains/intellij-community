enum My {
  foo, bar
}

My var = 'foo'
var = <warning descr="Cannot find enum constant 'fail' in enum 'My'">'fail'</warning>

var = "fo"+"o"
var="fo${'o'}"

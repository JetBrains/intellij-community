// IGNORE_K2
annotation class A

class B

@A(B.class<error descr="Name expected">)</error><EOLError descr="Expecting ','"></EOLError>
<error descr="[TOO_MANY_ARGUMENTS]"><error descr="[TOO_MANY_ARGUMENTS]">fun <error descr="[ANONYMOUS_FUNCTION_WITH_NAME]">f</error>() {}</error></error><EOLError descr="Expecting a top level declaration"></EOLError><EOLError descr="Expecting ')'"></EOLError>

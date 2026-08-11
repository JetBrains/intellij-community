// JET-11 Redeclaration & Forward reference for classes cause an exception
<error descr="[REDECLARATION]">open class NoC</error>
class NoC1 : NoC()
<error descr="[REDECLARATION]">open class NoC</error>

def arrayConstructorRef = Integer[]::new
def arrayConstructorRef2 = int[][]::new
def arrayConstructorRef3 = String[][][]::new

arrayConstructorRef<warning descr="'arrayConstructorRef' cannot be applied to '()'">()</warning>
arrayConstructorRef(1)
arrayConstructorRef<warning descr="'arrayConstructorRef' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(1, 2)</warning>

arrayConstructorRef2<warning descr="'arrayConstructorRef2' cannot be applied to '()'">()</warning>
arrayConstructorRef2(1)
arrayConstructorRef2(1, 2)
arrayConstructorRef2<warning descr="'arrayConstructorRef2' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">(1, 2, 3)</warning>

arrayConstructorRef3<warning descr="'arrayConstructorRef3' cannot be applied to '()'">()</warning>
arrayConstructorRef3(1)
arrayConstructorRef3(1, 2)
arrayConstructorRef3(1, 2, 3)
arrayConstructorRef3<warning descr="'arrayConstructorRef3' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer, java.lang.Integer)'">(1, 2, 3, 4)</warning>

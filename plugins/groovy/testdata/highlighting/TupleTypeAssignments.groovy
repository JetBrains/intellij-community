def foo = [1, 2, 3]
Double d = <warning descr="Constructor 'Double' in 'java.lang.Double' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">[1, 2, 3]</warning>
List<Double> <warning descr="Cannot assign 'ArrayList<String>' to 'List<Double>'">list</warning> = ["1", "2"]
List<Double> doubleList = [1, 2]
List<Double> secondDoubleList = [1.2, 2.5]
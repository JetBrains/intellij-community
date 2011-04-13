def foo = [1, 2, 3]
Double d = <warning descr="Cannot assign 'ArrayList<Integer>' to 'Double'">[1, 2, 3]</warning>
List<Double> list = <warning descr="Cannot assign 'ArrayList<String>' to 'List<Double>'">["1", "2"]</warning>
List<Double> doubleList = [1, 2]
List<Double> secondDoubleList = [1.2, 2.5]
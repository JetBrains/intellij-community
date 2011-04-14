java.util.List<java.lang.Integer> list = new java.util.ArrayList<java.lang.Integer>(3);
list.add(1);
list.add(2);
list.add(3);
java.util.List<Integer> list1 = new java.util.ArrayList<Integer>(2);
list1.add(3);
list1.add(4);
while (!org.codehaus.groovy.runtime.DefaultGroovyMethods.minus(list, list1).isEmpty()) list = org.codehaus.groovy.runtime.DefaultGroovyMethods.minus(list, 1);

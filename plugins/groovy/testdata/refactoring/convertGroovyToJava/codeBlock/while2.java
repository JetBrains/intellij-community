int i = 5;
java.util.List<Integer> list = new java.util.ArrayList<Integer>(4);
list.add(1);
list.add(2);
list.add(3);
list.add(4);
while (!org.codehaus.groovy.runtime.DefaultGroovyMethods.find(list, new groovy.lang.Closure(this, this) {
  boolean doCall(java.lang.Integer it) {
    return a.equals(it);
  }
})) a=a-1;

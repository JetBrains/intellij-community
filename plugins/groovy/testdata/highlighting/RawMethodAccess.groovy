class Arr<T>{
  void add(T item) {}
  T get(int i){}
}
Arr l=new Arr();
l.add("abc");
Date s=<warning descr="Cannot assign 'Object' to 'Date'">l.get(0)</warning>;
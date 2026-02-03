import groovy.transform.TupleConstructor

@TupleConstructor()
class Person {
  String name
  private boolean active = false
  List likes
}

println new Person<warning descr="Constructor 'Person' in 'Person' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], java.lang.Boolean)'">('mrhaki', ['Groovy', 'Java'], true)</warning>
println new Person('mrhaki', ['Groovy', 'Java'])
println new Person('mrhaki')

//-------------------------------

@TupleConstructor(includeFields=true)
class Person2 {
  String name
  List likes
  private boolean active = false
}

println new Person2('mrhaki', ['Groovy', 'Java'], true)
println new Person2('mrhaki', ['Groovy', 'Java'])
println new Person2<warning descr="Constructor 'Person2' in 'Person2' cannot be applied to '(java.lang.String, java.lang.Boolean, [java.lang.String, java.lang.String])'">('mrhaki', true, ['Groovy', 'Java'])</warning>

//-------------------------------

@TupleConstructor(force=true)
class Person3 {
  String name
  List likes
  private boolean active = false

  Person3(boolean active) {
    this.active = active
  }
}

println new Person3('mrhaki', ['Groovy', 'Java'])
println new Person3<warning descr="Constructor 'Person3' in 'Person3' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], java.lang.Boolean)'">('mrhaki', ['Groovy', 'Java'], true)</warning>
println new Person3(true)
println new Person3<warning descr="Constructor 'Person3' in 'Person3' cannot be applied to '(java.lang.Boolean, java.lang.Boolean)'">(true, false)</warning>

//-------------------------------

@TupleConstructor(includeFields=true)
class Person4 {
  String name
  List likes
  private boolean active = false
}

@TupleConstructor(callSuper=true, includeSuperProperties=true, includeSuperFields=true)
class Student extends Person4 {
  List courses
}

println new Student('mrhaki', ['Groovy', 'Java'], true, ['IT'])
println new Student<warning descr="Constructor 'Student' in 'Student' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], [java.lang.String])'">('mrhaki', ['Groovy', 'Java'], ['IT'])</warning>
println new Student<warning descr="Constructor 'Student' in 'Student' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], [java.lang.String], java.lang.Boolean)'">('mrhaki', ['Groovy', 'Java'], ['IT'], true)</warning>

//-------------------------------

@TupleConstructor(excludes="name, active")
class Person5 {
  String name = ""
  List likes = []
  private boolean active = false
}
println new Person5<warning descr="Constructor 'Person5' in 'Person5' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String])'">('mrhaki', ['Groovy', 'Java'])</warning>
println new Person5(['Groovy', 'Java'])
println new Person5<warning descr="Constructor 'Person5' in 'Person5' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], java.lang.Boolean)'">('mrhaki', ['Groovy', 'Java'], true)</warning>
println new Person5<warning descr="Constructor 'Person5' in 'Person5' cannot be applied to '(java.lang.Boolean)'">(true)</warning>

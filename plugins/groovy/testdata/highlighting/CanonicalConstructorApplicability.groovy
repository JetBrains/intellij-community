import groovy.transform.Canonical

@Canonical
class Person4 {
  String name
  List likes
  private boolean active = false
}

println new Person4('mrhaki', ['Groovy', 'Java'])
println new Person4<warning descr="Constructor 'Person4' in 'Person4' cannot be applied to '(java.lang.String, [java.lang.String, java.lang.String], java.lang.Boolean)'">('mrhaki', ['Groovy', 'Java'], true)</warning>
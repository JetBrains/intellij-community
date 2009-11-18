public def createRegisteredPerson(String username,
                                  String password,
                                  String email,
                                  String ipAddress,
                                  String roleName = null,
                                  String firstName = null,
                                  String lastName = null,
                                  Date birthday = null,
                                  String bio = null,
                                  String homepage = null,
                                  Date timeZone = null,
                                  String country = null,
                                  String city = null,
                                  String jabber = null,
                                  String site = null,
                                  String sex = null) {
}

createRegisteredPerson('name', 'pswd', 'email', 'ip', 'role', 'firstName', 'lastName', null, 'bio', 'page')

def foo(String a, Date b = null, int i = -1, String c, String d = 'd', String e, String f) {}


foo('aa', 'cc', 'dd', 'ee')
foo('a', null, 'c', 'e')
foo('a', null, 'd', 'c', 'e')

  foo<warning descr="'foo' in 'MethodCallWithDefaultParameters' cannot be applied to '(java.lang.String, null, java.lang.Integer, java.lang.String, java.lang.String)'">('a', null, 1, 'c', 'e')</warning>
  foo<warning descr="'foo' in 'MethodCallWithDefaultParameters' cannot be applied to '(java.lang.String, java.lang.String, java.lang.String)'">("aa", 'cc', 'ee')</warning>

foo('aa', null, 'cc', 'dd', 'ee')
  foo<warning descr="'foo' in 'MethodCallWithDefaultParameters' cannot be applied to '(java.lang.String, null, java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)'">('aa', null, 1, 'cc', 'dd', 'ee', 'f', 'g')</warning>

foo('a', 'cc', 'dd', 'ee')
  foo<warning descr="'foo' in 'MethodCallWithDefaultParameters' cannot be applied to '(java.lang.String, java.lang.Integer, java.lang.String, java.lang.String, java.lang.String)'">('a', -1, 'cc', 'dd', 'ee')</warning>


def bar(String a, Date b = null, int i = -1, String c, String d = 'd', String e, String... f) {}


bar('aa', 'cc', 'dd', 'ee')
bar('a', null, 'c', 'e')
bar('a', null, 'd', 'c', 'e')

bar('a', null, 1, 'c', 'e')
bar("aa", 'cc', 'ee')

bar('aa', null, 'cc', 'dd', 'ee')
bar('aa', null, 1, 'cc', 'dd', 'ee', 'f', 'g')

bar('a', 'cc', 'dd', 'ee')
  bar<warning descr="'bar' in 'MethodCallWithDefaultParameters' cannot be applied to '(java.lang.String, java.lang.Integer, java.lang.String, java.lang.String, java.lang.String)'">('a', -1, 'cc', 'dd', 'ee')</warning>

def go(String a, String b = 'b', String c, int ... i) {}

go('a', 'c', 1, 2, 3);
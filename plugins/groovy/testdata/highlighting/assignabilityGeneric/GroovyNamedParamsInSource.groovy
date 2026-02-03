import groovy.transform.NamedParam
import groovy.transform.NamedParams
import org.example.BaseClass
import org.example.DerivedClass
import org.example.SideClass

static <T> T methodWithRawType(@NamedParams([@NamedParam(value = 'name', type = String)]) Map obj, T value) {
  return value
}

static <T> T methodWithBoundType(@NamedParams([@NamedParam(value = 'name', type = String)]) Map<String, String> obj, T value) {
  return value
}

static <T> T methodWithObjectType(@NamedParams([@NamedParam(value = 'name', type = String)]) Map<String, Object> obj, T value) {
  return value
}


static void main(String[] args) {
          DerivedClass obj = new DerivedClass()

          DerivedClass derivedClass = methodWithRawType(name: "Stri", obj)

          BaseClass upper = methodWithRawType(name: "Stri", obj)

          SideClass <warning descr="Cannot assign 'DerivedClass' to 'SideClass'">s</warning> = methodWithRawType(name: "S", obj)

          BaseClass nonDeclaredParam = methodWithRawType(name: "S", constructorArgs: ["foo", "bar"], obj)

          BaseClass <warning descr="Cannot assign 'Object' to 'BaseClass'">wrongArguments</warning> = methodWithBoundType<warning descr="'methodWithBoundType' in 'GroovyNamedParamsInSource' cannot be applied to '(['name':java.lang.String, 'param':org.example.SideClass], org.example.DerivedClass)'">(name: "S", param: new SideClass(), obj)</warning>
          
          BaseClass base = methodWithObjectType(name: "S", side: new SideClass(), obj)
}
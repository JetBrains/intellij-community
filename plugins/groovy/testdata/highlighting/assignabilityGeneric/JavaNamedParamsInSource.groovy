import static org.example.Util.*

class BaseClass {}

class DerivedClass extends BaseClass {}

class SideClass {}

static void main(String[] args) {
          DerivedClass obj = new DerivedClass()

          DerivedClass derivedClass = methodWithRawType(name: "Stri", obj)

          BaseClass upper = methodWithRawType(name: "Stri", obj)

          SideClass <warning descr="Cannot assign 'DerivedClass' to 'SideClass'">s</warning> = methodWithRawType(name: "S", obj)

          BaseClass nonDeclaredParam = methodWithRawType(name: "S", constructorArgs: ["foo", "bar"], obj)

          BaseClass <warning descr="Cannot assign 'Object' to 'BaseClass'">wrongArguments</warning> = methodWithBoundType<warning descr="'methodWithBoundType' in 'org.example.Util' cannot be applied to '(['name':java.lang.String, 'param':SideClass], DerivedClass)'">(name: "S", param: new SideClass(), obj)</warning>
          
          BaseClass base = methodWithObjectType(name: "S", side: new SideClass(), obj)
}
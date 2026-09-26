package com.example

interface MyService {
  fun doSomething()
}

class MyServiceImpl : MyService {
  override fun doSomething() {}
  fun implOnly() {}
}

@Suppress("USELESS_CAST")
class Consumer {
  private var field: MyService? = null

  // Unsafe cast (as) to subclass - should report
  fun castToImpl(service: MyService) {
    (<warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">service as MyServiceImpl</warning>).implOnly()
  }

  // Safe cast (as?) - OK, won't crash
  fun safeCastToImpl(service: MyService) {
    (service as? MyServiceImpl)?.implOnly()
  }

  // Cast to same type - OK
  fun castToSameType(service: MyService) {
    (service as MyService).doSomething()
  }

  // From field
  fun fromField() {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">field as MyServiceImpl</warning>
  }
}

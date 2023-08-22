package server

interface InterfaceWithImpl {
    fun foo() = 1
}

public class InterfaceWithDelegatedWithImpl(f: InterfaceWithImpl) : InterfaceWithImpl by f

fun test(twdwi: InterfaceWithDelegatedWithImpl) = twdwi.foo()


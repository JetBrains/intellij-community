package server

interface InterfaceNoImpl {
    fun foo()
}

public class InterfaceWithDelegatedNoImpl(f: InterfaceNoImpl) : InterfaceNoImpl by f

fun test(twdni: InterfaceWithDelegatedNoImpl) = twdni.foo()


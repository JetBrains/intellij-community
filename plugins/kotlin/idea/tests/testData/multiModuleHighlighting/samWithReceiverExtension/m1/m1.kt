package m1

public fun testUseAsReceiver(api: javaInterface.API) {
    api.useM1A<String> {
        this.length
    }
    api.useM1B<String> {
        <error descr="[NO_THIS]">this</error>.<error descr="[DEBUG]">length</error>
    }
    api.useM2A<String> {
        this.length
    }
    api.useM2B<String> {
        <error descr="[NO_THIS]">this</error>.<error descr="[DEBUG]">length</error>
    }
}

public fun testUseAsParameter(api: javaInterface.API) {
    api.useM1A<String> {
        <error descr="[UNRESOLVED_REFERENCE]">it</error>.<error descr="[DEBUG]">length</error>
    }
    api.useM1B<String> {
        it.length
    }
    api.useM2A<String> {
        <error descr="[UNRESOLVED_REFERENCE]">it</error>.<error descr="[DEBUG]">length</error>
    }
    api.useM2B<String> {
        it.length
    }
}
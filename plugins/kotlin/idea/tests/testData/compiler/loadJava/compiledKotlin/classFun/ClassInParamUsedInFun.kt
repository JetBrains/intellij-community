package test

class ClassParamUsedInFun<in T> {
    fun f(t: T): Int = 1
}

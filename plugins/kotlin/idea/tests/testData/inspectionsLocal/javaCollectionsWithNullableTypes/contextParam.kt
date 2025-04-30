// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters
import java.util.concurrent.ConcurrentHashMap

class A {
    private context(string: ConcurrentHashMap<String, String?<caret>>) fun test() {}
}

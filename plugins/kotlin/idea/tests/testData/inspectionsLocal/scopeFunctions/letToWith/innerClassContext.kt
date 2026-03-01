// WITH_STDLIB
// FIX: Convert to 'with'
// IGNORE_K1
class Outer {
    val outerProp = "outer"
    
    inner class Inner {
        val innerProp = "inner"
        
        fun test(user: User) {
            user.<caret>let {
                "${it.name} in ${this@Outer.outerProp} and ${this@Inner.innerProp}"
            }
        }
    }
}

class User(val name: String)
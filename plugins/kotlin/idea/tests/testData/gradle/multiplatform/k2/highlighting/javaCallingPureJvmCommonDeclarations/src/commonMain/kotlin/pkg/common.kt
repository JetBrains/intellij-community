//region Test configuration
// - hidden: line markers
//endregion
package pkg


// KT-36740
class MyException : RuntimeException()

// KT-68169
fun someFunWithString(s: String) {}

// KT-40059
expect class EXPECT

// KT-71429
fun block(block: (String) -> Int): Int = 0

class Provider {
    val expect: EXPECT = TODO()
}

// KT-37783
expect open class MPPSuper()
class CommonChild : MPPSuper()

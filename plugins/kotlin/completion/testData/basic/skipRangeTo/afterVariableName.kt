// REGISTRY: ide.completion.command.force.enabled true
package foo

class Test {
    fun a(){
        val b..<caret> = "b"
    }
}

// ABSENT: fun
// ABSENT: class

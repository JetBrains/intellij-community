// REGISTRY: ide.completion.command.force.enabled true
package foo

class Test..<caret> {
    fun a(){
        val b = "b"
    }
}

// ABSENT: fun
// ABSENT: class
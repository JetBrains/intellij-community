// REGISTRY: ide.completion.command.force.enabled true
package foo

class Something

class Test {
    fun a(){
        val b2 = Something()
        val b = Something()..<caret>
    }
}

// ABSENT: fun
// ABSENT: class
// ABSENT: b2
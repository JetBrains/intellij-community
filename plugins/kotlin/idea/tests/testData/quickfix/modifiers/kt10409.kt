// "Make 'IterablePipeline' 'abstract'" "true"
// ERROR: One type argument expected for interface Pipeline<TPipeline>
// K2_ERROR: 'pipe' overrides nothing. Potential signatures for overriding:<br>fun pipe(block: ??? (Wrong number of type arguments)): Unit
// K2_ERROR: Class 'IterablePipeline' is not abstract and does not implement abstract members:<br>fun pipe(block: ??? (Wrong number of type arguments)): Unit<br>fun completelyAbstract(): Unit
// K2_ERROR: One type argument expected for 'interface Pipeline<TPipeline> : Any'.
// K2_AFTER_ERROR: 'pipe' overrides nothing. Potential signatures for overriding:<br>fun pipe(block: ??? (Wrong number of type arguments)): Unit
// K2_AFTER_ERROR: One type argument expected for 'interface Pipeline<TPipeline> : Any'.

// Actually this test is about getting rid of assertion happenning while creating quick fixes
// See KT-10409
interface Pipeline<TPipeline> {
    fun pipe(block: Pipeline<TPipeline, String>)
    fun completelyAbstract()
}

<caret>class IterablePipeline<T> : Pipeline<T> {
    override fun pipe(block: Pipeline<T>) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// "Implement members" "true"
// WITH_STDLIB
// K2_ERROR: Class 'C' is not abstract and does not implement abstract base class members:<br>fun write(p0: CharArray!, p1: Int, p2: Int): Unit<br>fun flush(): Unit<br>fun close(): Unit

class<caret> C : java.io.Writer(), java.lang.Appendable

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix

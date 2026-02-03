class PsiElement() {

    val operationReference = PsiElement()
    lateinit var myTextRange: TextRange

    public constructor(textRange: TextRange) : this() {
        myTextRange = textRange;
    }

    public fun get<caret>TextRange(): TextRange {
        return myTextRange;
    }
}

class TextRange(val startOffset: Int, val endOffset: Int) {

    fun containsOffset(caretOffset: Int): Boolean {
        return true
    }

}


class ToTest {
    fun f() {
        val element = PsiElement(TextRange(1, 5))
        val opRef = element.operationReference
        if (!opRef.getTextRange().containsOffset(2)) return

        System.out.println(element.getTextRange())
        System.out.println(opRef.getTextRange().containsOffset(2))
        System.out.println(opRef.getTextRange().containsOffset(5))
        System.out.println(element.getTextRange().startOffset + 1)
        System.out.println(element.getTextRange().startOffset + 2)
        if (!opRef.getTextRange().containsOffset(3)) return


    }
}
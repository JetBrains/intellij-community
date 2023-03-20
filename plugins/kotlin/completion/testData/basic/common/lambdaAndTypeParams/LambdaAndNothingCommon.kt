import kotlin.experimental.ExperimentalTypeInference

interface MyBaseList<E> {
    fun addMemberBase(element: E)
}

class MyList<E>: MyBaseList<E> {

    override fun addMemberBase(element: E) { TODO() }

    fun addMember(element: E): Boolean { TODO() }
    fun addMemberWithBlock(element: E, block: (Exception) -> Nothing): Boolean { TODO() }

    fun MyList<E>.addMemberExt(element: E): Boolean { TODO() }

    companion object {
        fun <E> addCompnanion(element: E): Boolean { TODO() }
        fun <E> MyList<E>.addCompnanionExt(element: E): Boolean { TODO() }
    }
}

fun <E> addTopLevel(element: E): Boolean { TODO() }
fun <E> MyList<E>.addTopLevelExt(element: E): Boolean { TODO() }


@OptIn(ExperimentalTypeInference::class)
fun <E> buildMyList(@BuilderInference builderAction: MyList<E>.() -> Unit): List<E> = TODO()

class TestClass {

    fun test() {
        buildMyList { // this: MutableList<out Any?>
            buildMyList<String> {
                // At this point we have two receivers: (1): MutableList<out Any?>  and (2): MutableList<String>
                // Substitution for the first one results in Nothing type, which we restore to its original letter (E, T, etc.)
                add<caret>
            }
        }
    }
}

// EXIST: {"lookupString":"addMember","tailText":"(element: E)","typeText":"Boolean"}
// EXIST_JAVA_ONLY: {"lookupString":"addMemberWithBlock","tailText":"(element: String, block: (Exception /* = Exception */) -> Nothing)","typeText":"Boolean"}
// EXIST_JAVA_ONLY: {"lookupString":"addMemberWithBlock","tailText":"(element: E, block: (Exception /* = Exception */) -> Nothing)","typeText":"Boolean"}
// EXIST_JS_ONLY: {"lookupString":"addMemberWithBlock","tailText":"(element: String, block: (Exception) -> Nothing)","typeText":"Boolean"}
// EXIST_JS_ONLY: {"lookupString":"addMemberWithBlock","tailText":"(element: E, block: (Exception) -> Nothing)","typeText":"Boolean"}
// EXIST: {"lookupString":"addMemberBase","tailText":"(element: E)","typeText":"Unit"}
// EXIST: {"lookupString":"addTopLevel","tailText":"(element: E) (<root>)","typeText":"Boolean"}
// EXIST: {"lookupString":"addTopLevelExt","tailText":"(element: Any?) for MyList<E> in <root>","typeText":"Boolean"}
// EXIST: {"lookupString":"addCompnanionExt","tailText":"(element: Any?) for MyList<E> in MyList.Companion (<root>)","typeText":"Boolean"}
// "Create member function 'Q.myf'" "true"
class R(val f: (Int) -> Unit)

class Q {
    val r = R(this::myf<caret>)
}
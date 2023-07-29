fun yy(): Int = 5

fun f(a: Int): Int {
    return<caret> try {
        val q = a * a
        q
    } catch (e: Exception) {
        0
    } catch (e: Throwable) {
        -1
    } finally {
      println()
    }
}

//HIGHLIGHTED: return
//HIGHLIGHTED: f
//HIGHLIGHTED: 0
//HIGHLIGHTED: q
//HIGHLIGHTED: -1
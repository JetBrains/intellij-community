// WITH_STDLIB
fun test(cn: Cn?): String? {
    var query: Q? = null
    var result: String? = null
    if (cn != null) {
        try {
            query = cn.query()
            result = query.data()
        }
        catch(e:Exception) {}
        finally {
            try {
                query?.close()
            }
            catch (e:Exception) {}
        }
    }
    return result
}
interface Cn {
    fun query(): Q
}
interface Q {
    fun data(): String
    fun close()
}
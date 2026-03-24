private fun foo(list: Collection<String>): List<Mapping> {
    return list.map {
        Mapping(it as String)
    }.toList()
}

class Mapping(c: String) {
}

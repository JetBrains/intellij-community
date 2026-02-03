class Edge {
    Integer v1
    Integer v2
    Integer weight

    Edge(Integer v1, Integer v2, Integer w) {
        this.v1 = v1
        this.v2 = v2
        this.weight = w
    }

    boolean contains(Integer v) { v1 == v || v2 == v }
    Integer otherThan(Integer v) { v1 == v ? v2 : v1 }
}

class Graph {
    List<Integer> V
    Collection<Edge> E

    // Dumbest implementation
    List<Integer> getIncident(Integer v) { E.findAll{ it.contains(v) }.collect{ it.otherThan(v) } }
    Edge findEdge(Integer v1, Integer v2) { E.find{ it.contains(v1) && it.contains(v2) } }
    Number weight(Integer v1, Integer v2) { findEdge(v1, v2)?.weight ?: Double.POSITIVE_INFINITY }
}

Collection<Edge> prim(Graph G) {
    Set<Edge> T = []
    Map<Integer, Number> d = [:] + G.V.collect { new MapEntry(it, Double.POSITIVE_INFINITY) }
    Map<Integer, Integer> p = [:] + G.V.collect { new MapEntry(it, null) }
    d[G.V[0]] = 0

    List<Integer> Q = G.V.collect{it}
    Q.metaClass.extractMin = { ->
        // Dumbest implementation
        int minimum = Q.min { d[it] }
        int indexOfMinimum = Q.indexOf(minimum) // here Groovy fails to make a shortcut.
        return Q.remove(indexOfMinimum)
    }

    Integer v = Q.extractMin()
    while (!Q.isEmpty()) {
        G.getIncident(v).each { Integer u ->
            if(Q.contains(u) && G.weight(u, v) < d[u]) {
                d[u] = G.weight(u, v)
                p[u] = v
            }
        }
        v = Q.extractMin()
        T << G.findEdge(p[v], v)
    }

    T
}

void testPrim() {
    Set<Edge> expected = [
        new Edge(1, 2, 1),
        new Edge(2, 3, 1),
        new Edge(3, 4, 1),
        new Edge(4, 5, 1),
    ]

    Set<Edge> edges = [
        new Edge(1, 3, 2),
        new Edge(1, 4, 3),
        new Edge(1, 5, 2),
        new Edge(2, 4, 7),
        new Edge(3, 5, 2),
    ]

    Graph G = new Graph(V: 1..5, E: expected + edges)

    Set<Edge> T = prim(G)
    assert expected.equals(T)
    println "it worked!"
}

testPrim()

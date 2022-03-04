package org.jetbrains.deft.impl

class AlienObjectRefError(
    val graph: ObjStorageImpl.ObjGraph?,
    val obj: ObjImpl?,
    val target: ObjImpl?,
    val targetGraph: ObjStorageImpl.ObjGraph,
) : Error(
    "Graph $graph, object $obj: " +
            "cannot reference ${target ?: "object"} from graph $targetGraph. " +
            "Use `load(obj.id)` to create copy of object." +
            "See https://deft.jb.gg/docs/objects-graph.html for more details"
) {

}
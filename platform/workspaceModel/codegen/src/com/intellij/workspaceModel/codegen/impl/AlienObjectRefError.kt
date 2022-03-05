package org.jetbrains.deft.impl

import com.intellij.workspaceModel.codegen.impl.ObjGraph

class AlienObjectRefError(
  val graph: ObjGraph?,
  val obj: ObjImpl?,
  val target: ObjImpl?,
  val targetGraph: ObjGraph,
) : Error(
    "Graph $graph, object $obj: " +
            "cannot reference ${target ?: "object"} from graph $targetGraph. " +
            "Use `load(obj.id)` to create copy of object." +
            "See https://deft.jb.gg/docs/objects-graph.html for more details"
) {

}
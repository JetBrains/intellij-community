package com.jetbrains.reactivemodel

import com.intellij.openapi.util.Key
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapDiff
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.assocModelWithPath
import java.util.ArrayList

public object Last

public val pathKey: Key<Path> = Key.create("com.jetbrains.reactivemodel.Path")

public data class Path(val components: List<Any>) {
  public constructor(vararg cs: Any) : this(listOf(*cs))

  public fun div(a: Any): Path {
    val list = ArrayList(components)
    list.add(a)
    return Path(list)
  }

  public fun dropLast(n: Int) : Path {
    return Path(components.take(components.size() - n))
  }
}

fun Path.isPrefixOf(path: Path): Boolean =
    this.components
        .zip(path.components)
        .all { it.first == it.second }

fun<M : AssocModel<K, M>, K> Path.putIn(mapModel: M, model: Model): M {
  if (components.isEmpty()) {
    return model as M
  }

  val first = components.first() as K
  val child = if (first is Last) null
  else mapModel.find(first as K)

  return if (child !is AssocModel<*, *>) {
    val leaf =
        if (components.size() == 1) model
        else assocModelWithPath(Path(components.drop(1)), model)

    if (first is Last) {
      (mapModel as ListModel).add(leaf) as M
    } else {
      mapModel.assoc(first, leaf) as M
    }
  } else {
    if (components.size() == 1) {
      mapModel.assoc(first, model)
    } else {
      mapModel.assoc(first, Path(components.drop(1)).putIn(child, model))
    }
  }
}

fun MapModel.putIn(p: Path, model: Model): MapModel  = p.putIn(this, model)

fun<M : AssocModel<*, M>> Path.getIn(mapModel: M): Model? =
    components.fold<Any, Model?>(mapModel) { model, component ->
      if (model is AssocModel<*, *>) (model as AssocModel<Any, *>).find(component)
      else null
    }

fun MapModel.getIn(path: Path): Model? = path.getIn(this)

fun Path.getIn(mapDiff: MapDiff): Diff<Model>? =
    components.fold<Any, Diff<Model>?>(mapDiff) { mapDiff, component ->
      if (mapDiff is MapDiff ) mapDiff.diff [component]
      else null
    }

fun Path.toList() : ListModel = ListModel(components.map { PrimitiveModel(it) })
package com.jetbrains.reactivemodel.models

import com.github.krukow.clj_ds.PersistentMap
import com.github.krukow.clj_ds.Persistents
import com.github.krukow.clj_lang.IPersistentMap
import com.github.krukow.clj_lang.PersistentHashMap
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.util.emptyMeta
import com.jetbrains.reactivemodel.util.withEmptyMeta
import com.jetbrains.reactivemodel.util.withMeta
import java.util.HashMap

public data class MapModel(val hmap: PersistentHashMap<String, Model> = PersistentHashMap.emptyMap(),
                           val metadata: IPersistentMap<String, *> = emptyMeta())
: AssocModel<String, MapModel>, Map<String, Model?> by hmap {

  override fun <T> acceptVisitor(visitor: ModelVisitor<T>): T = visitor.visitMapModel(this)

  public constructor(m: Map<String, Model>, metadata: IPersistentMap<String, *> = emptyMeta())
  : this(PersistentHashMap.create(m), metadata)

  public override fun assoc(key: String, value: Model?): MapModel =
      if (value is AbsentModel) remove(key)
      else MapModel(hmap.plus(key, value), metadata)

  public override fun find(key: String): Model? = hmap.get(key)
  public fun remove(k: String): MapModel = MapModel(hmap.minus(k))

  override fun diffImpl(other: Model): Diff<Model>? {
    if (other !is MapModel) {
      throw AssertionError()
    }

    val diff = HashMap<String, Diff<Model>>()
    hmap.keySet().union(other.hmap.keySet()).forEach {
      val value = this.find(it)
      val otherValue = other.find(it)
      if (value == null && otherValue != null) {
        diff.put(it, ValueDiff(otherValue))
      } else if (value != null && otherValue != null) {
        val valuesDiff = value.diff(otherValue)
        if (valuesDiff != null) {
          diff.put(it, valuesDiff)
        }
      } else {
        diff.put(it, ValueDiff(AbsentModel()))
      }
    }
    if (diff.isEmpty()) return null
    else return MapDiff(diff)
  }

  override fun patch(diff: Diff<Model>): MapModel {
    if (diff is ValueDiff<*>) {
      if (diff.newValue is AbsentModel) {
        throw AssertionError()
      }
      if (diff.newValue !is MapModel) {
        throw AssertionError()
      }
      return diff.newValue
    }
    if (diff !is MapDiff) {
      throw AssertionError()
    }
    var self = this
    for ((k, d) in diff.diff) {
      val value = this.find(k)
      if (value == null) {
        if (d !is ValueDiff<*>) {
          throw AssertionError()
        }
        self = self.assoc(k, d.newValue)
      } else if (d is ValueDiff<*> && d.newValue is AbsentModel) {
        self = self.remove(k)
      } else {
        self = self.assoc(k, value.patch(d))
      }
    }
    return self
  }

  override val meta: IPersistentMap<String, *>
    get() = metadata
}

public data class MapDiff(val diff: Map<String, Diff<Model>>) : Diff<MapModel> {
  override fun <T> acceptVisitor(visitor: DiffVisitor<T>): T = visitor.visitMapDiff(this)
}

public fun assocModelWithPath(p: Path, m: Model): MapModel =
    p.components.foldRight(m) { comp, m ->
      MapModel(hashMapOf(comp as String to m))
    } as MapModel

package com.jetbrains.reactivemodel

import com.jetbrains.reactivemodel.models.*

public trait ModelVisitor<T> {
  fun visitMapModel(mapModel: MapModel): T
  fun visitListModel(listModel: ListModel): T
  fun visitPrimitiveModel(primitive: PrimitiveModel<*>): T
  fun visitAbsentModel(absent: AbsentModel): T
}

public trait DiffVisitor<T> {
  fun visitMapDiff(mapDiff: MapDiff): T
  fun visitListDiff(listDiff: ListDiff): T
  fun visitValueDiff(valueDiff: ValueDiff<*>): T
  fun visitPrimitiveDiff(primitiveDiff: PrimitiveDiff): T
}

public trait Model : HasMeta {
  fun diff(other: Model): Diff<Model>? {
    if (other == this) {
      return null
    }
    if (other.javaClass != javaClass) {
      return ValueDiff(other)
    }
    return diffImpl(other)
  }

  fun diffImpl(other: Model): Diff<Model>?

  fun patch(diff: Diff<Model>): Model

  fun<T> acceptVisitor(visitor: ModelVisitor<T>): T
}

public trait Diff<out M : Model> {
  fun<T> acceptVisitor(visitor: DiffVisitor<T>): T
}

public data class ValueDiff<T : Model>(val newValue: T) : Diff<Model> {
  override fun <T> acceptVisitor(visitor: DiffVisitor<T>): T = visitor.visitValueDiff(this)
}

trait AssocModel<in K, out TThis : AssocModel<K, *>> : Model {
  fun assoc(key: K, value: Model?): TThis
  fun find(key: K): Model?
}

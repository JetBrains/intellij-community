package com.jetbrains.reactivemodel

import com.jetbrains.reactivemodel
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.*
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.ArrayDeque
import java.util.HashMap
import java.util.Queue

public class ReactiveModel(val lifetime: Lifetime = Lifetime.Eternal, val diffConsumer: (MapDiff) -> Unit = {}) {
  public var root: MapModel = MapModel()
  private val subscriptions: MutableMap<Path, ModelSignal> = HashMap()
  private val transactionsQueue: Queue<(MapModel) -> MapModel> = ArrayDeque()
  private val transactionGuard = Guard()
  private val actionToHandler: MutableMap<String, (MapModel, MapModel) -> MapModel> = HashMap()

  companion object {
    private var cur : ReactiveModel? = null
    public fun current() : ReactiveModel? {
      return cur;
    }
  }

  private inner class ModelSignal(val path: Path, override val lifetime: Lifetime) : com.jetbrains.reactivemodel.Signal<Model?> {
    override val value: Model?
      get() = path.getIn(root)
  }

  public fun subscribe(lt: Lifetime = lifetime, path: Path): com.jetbrains.reactivemodel.Signal<Model?> {
    val signal = ModelSignal(path, lt)
    subscriptions[path] = signal
    lifetime += {
      subscriptions.remove(path)
    }
    return signal
  }

  public fun transaction(f: (MapModel) -> MapModel) {
    transactionsQueue.add(f)
    if (transactionGuard.locked) return
    transactionGuard.lock {
      while (transactionsQueue.isNotEmpty()) {
        val diff = performTransaction(transactionsQueue.poll())
        if (diff != null) {
          diffConsumer(diff)
        }
      }
    }
  }

  fun performTransaction(f: (MapModel) -> MapModel): MapDiff? {
    var oldModel = root
    var newModel = f(oldModel)
    val diff = oldModel.diff(newModel)
    if (diff == null) {
      return null
    }
    if (diff !is MapDiff) {
      throw AssertionError()
    }
    root = newModel
    fireUpdates(oldModel, newModel, diff)
    terminateLifetimes(oldModel, diff)
    return diff
  }

  class TerminateLifetimeVisitor(val oldModel: MapModel, val path: Path = Path()) : DiffVisitor<Unit> {
    override fun visitMapDiff(mapDiff: MapDiff) {
      mapDiff.diff.forEach { e ->
        e.value.acceptVisitor(TerminateLifetimeVisitor(oldModel, path / e.key))
      }
    }

    override fun visitValueDiff(valueDiff: ValueDiff<*>) {
      if(valueDiff.newValue is AbsentModel) {
        val oldModel = path.getIn(oldModel)
        if (oldModel is MapModel) {
          terminateLifetimesRec(oldModel)
        }
      }
    }

    override fun visitListDiff(listDiff: ListDiff) {}

    override fun visitPrimitiveDiff(primitiveDiff: PrimitiveDiff) {}

    private fun terminateLifetimesRec(oldModel: MapModel) {
      val lifetime = oldModel.meta.valAt("lifetime") as Lifetime?
      if(lifetime != null) {
        lifetime.terminate()
      } else {
        val map : Map<String, Model?> = oldModel.hmap // explicit type inference
        map.forEach { e ->
          val value = e.getValue()
          if(value is MapModel) {
            terminateLifetimesRec(value)
          }
        }
      }
    }
  }

  private fun terminateLifetimes(oldModel: MapModel, diff: MapDiff) {
    diff.acceptVisitor(TerminateLifetimeVisitor(oldModel))
  }

  private fun fireUpdates(oldModel: MapModel, newModel: MapModel, diff: MapDiff) {
    com.jetbrains.reactivemodel.updates {
      for ((path, signal) in subscriptions) {
        if (path.getIn(diff) != null) {
          com.jetbrains.reactivemodel.ReactGraph.scheduleUpdate(com.jetbrains.reactivemodel.Change(signal, path.getIn(oldModel), path.getIn(newModel)))
        }
      }
    }
  }

  public fun registerHandler(l: Lifetime, action: String, handler : (MapModel, MapModel) -> MapModel) {
    actionToHandler[action] = handler
    l += {actionToHandler.remove(action)}
  }

  fun dispatch(actionModel: Model, model: MapModel): MapModel {
    actionModel as MapModel
    cur = this
    val actionName = (actionModel["action"] as PrimitiveModel<String>).value
    val handler = actionToHandler[actionName]
    if (handler != null) {
      return handler(actionModel["args"] as MapModel, model)
    } else {
      println("unknown action $actionModel")
    }
    println(actionModel)
    return model
  }
}

fun test() {
  val mirror = ReactiveModel(Lifetime.Eternal, {
    println(it)
  })

  val model = ReactiveModel(Lifetime.Eternal, { diff ->
    mirror.performTransaction { m ->
      m.patch(diff)
    }
  })

  model.transaction { m ->
    MapModel(hashMapOf(
        "a" to PrimitiveModel(1),
        "b" to MapModel(hashMapOf(
            "c" to PrimitiveModel("some string"),
            "d" to PrimitiveModel("some other string"),
            "lookup" to ListModel(listOf(
                PrimitiveModel(1),
                PrimitiveModel(2)))
        ))))
  }

  val cSignal = model.subscribe(Lifetime.Eternal, Path("b", "c"))

  val mirrorBC = mirror.subscribe(Lifetime.Eternal, Path("b", "c"))
  com.jetbrains.reactivemodel.reaction(false, "mirror/b/c", mirrorBC) {
    println("mirror/b/c $it")
  }


  val cStr = com.jetbrains.reactivemodel.reaction(true, "b/c -> string", cSignal) {
    if (it == null) null
    else (it as PrimitiveModel<String>).value
  }

  com.jetbrains.reactivemodel.reaction(false, "println", cStr) {
    println("b/c $it")
  }

  val lookupSignal = mirror.subscribe(Lifetime.Eternal, Path("b", "lookup"))
  com.jetbrains.reactivemodel.reaction(false, "println lookup", lookupSignal) {
    println(it)
  }

  model.transaction { m ->
    var newModel = Path("b", "c").putIn(m, PrimitiveModel("new string"))
    newModel = Path("b", "lookup").putIn(newModel, ListModel(listOf(
        PrimitiveModel(1),
        PrimitiveModel(2),
        PrimitiveModel(4)
    )))
    newModel
  }

  model.transaction { m ->
    Path("b", "lookup", Last).putIn(m, PrimitiveModel(5))
  }

}

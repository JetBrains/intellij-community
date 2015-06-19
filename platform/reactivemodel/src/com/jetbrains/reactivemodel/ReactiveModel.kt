package com.jetbrains.reactivemodel

import com.jetbrains.reactivemodel.models.*
import com.jetbrains.reactivemodel.signals.*
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.ArrayDeque
import java.util.HashMap
import java.util.Queue

public class ReactiveModel(val lifetime: Lifetime = Lifetime.Eternal, val diffConsumer: (MapDiff) -> Unit = {}, val actionsDispatcher: (Model) -> Unit = {}) {
  public var root: MapModel = MapModel()
  private val subscriptions: MutableMap<Path, ModelSignal> = HashMap()
  private val transactionsQueue: Queue<(MapModel) -> MapModel> = ArrayDeque()
  private val transactionGuard = Guard()

  private inner class ModelSignal(val path: Path, override val lifetime: Lifetime) : Signal<Model?> {
    override val value: Model?
      get() = path.getIn(root)
  }

  public fun subscribe(lt: Lifetime = lifetime, path: Path): Signal<Model?> {
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
    return diff
  }

  private fun fireUpdates(oldModel: MapModel, newModel: MapModel, diff: MapDiff) {
    updates {
      for ((path, signal) in subscriptions) {
        if (path.getIn(diff) != null) {
          ReactGraph.scheduleUpdate(Change(signal, path.getIn(oldModel), path.getIn(newModel)))
        }
      }
    }
  }

  fun dispatch(action: Model) {
    actionsDispatcher(action)
  }
}

fun main(args: Array<String>) {
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
  reaction(false, "mirror/b/c", mirrorBC) {
    println("mirror/b/c $it")
  }


  val cStr = reaction(true, "b/c -> string", cSignal) {
    if (it == null) null
    else (it as PrimitiveModel<String>).value
  }

  reaction(false, "println", cStr) {
    println("b/c $it")
  }

  val lookupSignal = mirror.subscribe(Lifetime.Eternal, Path("b", "lookup"))
  reaction(false, "println lookup", lookupSignal) {
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

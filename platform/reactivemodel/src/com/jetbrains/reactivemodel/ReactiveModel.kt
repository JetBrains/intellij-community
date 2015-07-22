package com.jetbrains.reactivemodel

import com.github.krukow.clj_lang.IPersistentMap
import com.github.krukow.clj_lang.PersistentHashMap
import com.github.krukow.clj_lang.PersistentHashSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import com.jetbrains.reactivemodel.log.logTime
import com.jetbrains.reactivemodel.models.*
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.createMeta
import com.jetbrains.reactivemodel.util.lifetime
import java.util.ArrayDeque
import java.util.HashMap
import java.util.Queue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

public class ReactiveModel(val lifetime: Lifetime = Lifetime.Eternal, val diffConsumer: (MapDiff) -> Unit = {}) {
  public var root: MapModel = MapModel(meta = createMeta("lifetime", lifetime, INDEX_FIELD, PersistentHashMap.emptyMap<String, PersistentHashSet<Path>>()))
  public val name: String = "ReactiveModel" + counter.incrementAndGet()
  private val subscriptions: MultiMap<Path, ModelSignal> = MultiMap.create()
  private val tagSubs: MultiMap<String, TagSignal<*>> = MultiMap.create()
  private val transactionsQueue: Queue<(MapModel) -> MapModel> = ArrayDeque()
  private val transactionGuard = Guard()
  private val actionToHandler: MutableMap<String, (MapModel, MapModel) -> MapModel> = HashMap()
  private val LOG = Logger.getInstance("#com.jetbrains.reactivemodel.ReactiveModel")

  public var currentInit: Initializer? = null

  companion object {
    val INDEX_FIELD = "index"
    val counter = AtomicInteger()

    private var cur: ReactiveModel? = null
    public fun current(): ReactiveModel? {
      return cur;
    }
  }

  init {
    cur = cur ?: this
  }

  private inner class ModelSignal(val path: Path, override val lifetime: Lifetime) : Signal<Model?> {
    override val value: Model?
      get() = path.getIn(root)
  }

  private inner class TagSignal<T : Model>(val tag: Tag<T>, override val lifetime: Lifetime) : Signal<List<T>> {
    override val value: List<T>
      get() = tag.getIn(root)
  }

  public fun subscribe(lt: Lifetime = lifetime, path: Path): Signal<Model?> {
    val signal = ModelSignal(path, lt)
    subscriptions.putValue(path, signal)
    lt += {
      subscriptions.remove(path, signal)
    }
    return signal
  }

  public fun subscribe<T : Model>(lt: Lifetime = lifetime, tag: Tag<T>): TagSignal<T> {
    val signal = TagSignal(tag, lt)
    tagSubs.putValue(tag.name, signal)
    lt += {
      tagSubs.remove(tag.name, signal)
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

  fun performTransaction(f: (MapModel) -> MapModel): MapDiff? =
    logTime("transaction", 100) {
      var oldModel = root
      var newModel = f(oldModel)
      val diff = oldModel.diff(newModel) ?: return null
      if (diff !is MapDiff) {
        throw AssertionError()
      }
      newModel = updateIndexes(oldModel, newModel, diff)
      root = newModel
      fireUpdates(oldModel, newModel, diff)
      terminateLifetimes(oldModel, diff)
      return diff
    }

  class UpdateIndexVisitor(val model: MapModel, val oldModel: MapModel, val path: Path = Path()) : DiffVisitor<MapModel> {
    override fun visitMapDiff(mapDiff: MapDiff): MapModel {
      var newModel = model
      for (e in mapDiff.diff) {
        newModel = e.value.acceptVisitor(UpdateIndexVisitor(newModel, oldModel, path / e.key))
      }
      return newModel
    }

    override fun visitListDiff(listDiff: ListDiff): MapModel {
      return model
    }

    override fun visitValueDiff(valueDiff: ValueDiff<*>): MapModel {
      val newval = valueDiff.newValue
      if (newval is MapModel) {
        return processTagsRec(newval, path, true, model)
      }
      if (newval is AbsentModel) {
        val oldModel = path.getIn(oldModel)
        if (oldModel is MapModel) {
          return processTagsRec(oldModel, path, false, model)
        }
      }
      return model
    }

    override fun visitPrimitiveDiff(primitiveDiff: PrimitiveDiff): MapModel {
      return model
    }

    private fun processTagsRec(newval: MapModel, path: Path, add: Boolean, model: MapModel): MapModel {
      var newModel = model
      newval.forEach { e ->
        val value = e.value
        if (e.key == tagsField) {
          val values = value as ListModel
          values.forEach {
            it as PrimitiveModel<*>
            val tag = getTag(it.value as String)
            if (tag != null) {
              val index = newModel.meta.index()
              var vals: PersistentHashSet<Path> = index.valAt(tag.name, PersistentHashSet.emptySet<Path>())
              vals = if (add) vals.cons(path) else vals.disjoin(path)
              newModel = newModel.assocMeta(INDEX_FIELD, index.assoc(tag.name, vals))
            }
          }
        } else if (value is MapModel) {
          newModel = processTagsRec(value, path / e.key, add, newModel)
        }
      }
      return newModel
    }
  }


  private fun updateIndexes(oldModel: MapModel, newModel: MapModel, diff: MapDiff): MapModel {
    return diff.acceptVisitor(UpdateIndexVisitor(newModel, oldModel))
  }

  class TerminateLifetimeVisitor(val oldModel: MapModel, val path: Path = Path()) : DiffVisitor<Unit> {
    override fun visitMapDiff(mapDiff: MapDiff) {
      mapDiff.diff.forEach { e ->
        e.value.acceptVisitor(TerminateLifetimeVisitor(oldModel, path / e.key))
      }
    }

    override fun visitValueDiff(valueDiff: ValueDiff<*>) {
      if (valueDiff.newValue is AbsentModel) {
        val oldModel = path.getIn(oldModel)
        if (oldModel is MapModel) {
          terminateLifetimesRec(oldModel)
        }
      }
    }

    override fun visitListDiff(listDiff: ListDiff) {
    }

    override fun visitPrimitiveDiff(primitiveDiff: PrimitiveDiff) {
    }

    private fun terminateLifetimesRec(oldModel: MapModel) {
      val lifetime = oldModel.meta.lifetime()
      if (lifetime != null) {
        lifetime.terminate()
      } else {
        val map: Map<String, Model?> = oldModel.hmap // explicit type inference
        map.forEach { e ->
          val value = e.getValue()
          if (value is MapModel) {
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
    updates {
      for ((path, signals) in subscriptions.entrySet()) {
        for(signal in signals) {
          if (path.getIn(diff) != null) {
            ReactGraph.scheduleUpdate(Change(signal, path.getIn(oldModel), path.getIn(newModel)))
          }
        }
      }
      for ((name, signals) in tagSubs.entrySet()) {
        for(signal in signals) {
          if (oldModel.meta.index()[name] != newModel.meta.index()[name]) {
            assert(signal.tag.getIn(oldModel) != signal.tag.getIn(newModel))
            ReactGraph.scheduleUpdate(Change(signal, signal.tag.getIn(oldModel), signal.tag.getIn(newModel)))
          }
        }
      }
    }
  }

  public fun registerHandler(l: Lifetime, action: String, handler: (MapModel, MapModel) -> MapModel) {
    actionToHandler[action] = handler
    l += { actionToHandler.remove(action) }
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

@suppress("UNCHECKED_CAST")
private fun IPersistentMap<String, *>.index(): PersistentHashMap<String, PersistentHashSet<Path>> =
    this.valAt(ReactiveModel.INDEX_FIELD) as PersistentHashMap<String, PersistentHashSet<Path>>

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

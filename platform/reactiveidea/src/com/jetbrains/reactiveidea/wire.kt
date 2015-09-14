/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import clojure.lang.*
import com.corundumstudio.socketio.*
import com.corundumstudio.socketio.listener.ConnectListener
import com.corundumstudio.socketio.listener.DisconnectListener
import com.github.krukow.clj_ds.PersistentMap
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.*
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.UUID

fun serverModel(lifetime: Lifetime, port: Int,
                reactiveModels: VariableSignal<PersistentMap<String, ReactiveModel>>,
                initModelFunc: (ReactiveModel) -> Unit = {}) {

  val config = Configuration()
  config.setHostname("localhost")
  config.setPort(port)
  val sockConfig = SocketConfig()
  sockConfig.setReuseAddress(true)
  config.setSocketConfig(sockConfig)

  val server = SocketIOServer(config)
  // TODO would be fixed in 1.7.8 netty-socketio version. Should be removed after update
  server.setPipelineFactory(FixSocketIOChannelInitializer())

  fun getModel(client: SocketIOClient): ReactiveModel {
    val id = client.getSessionId().toString()
    var model: ReactiveModel? = reactiveModels.value[id]
    if (model == null) {
      model = createModel(Lifetime.create(lifetime).lifetime, client.getSessionId(), server)
      initModelFunc(model)
      reactiveModels.value = reactiveModels.value.plus(id, model)
      model.lifetime += {
        reactiveModels.value = reactiveModels.value.minus(id)
      }
    }
    return model
  }

  server.addConnectListener(object : ConnectListener {
    override fun onConnect(client: SocketIOClient) {
      UIUtil.invokeLaterIfNeeded {
        var model = getModel(client)
        val diff = MapModel().diff(model.root)
        if (diff != null) {
          val data = toClojure(diff)

          client.sendEvent("diff", RT.printString(data))
        }
      }
    }
  })


  server.addEventListener("diff", javaClass<String>()) { client, data, ackRequest ->
    val edn = RT.readString(data) as APersistentMap
    val diff = toDiff(edn)
    UIUtil.invokeLaterIfNeeded {
      getModel(client).performTransaction { m ->
        m.patch(diff)
      }
    }
  }

  server.addEventListener("action", javaClass<String>()) { client, data, ackRequest ->
    val action = toModel(RT.readString(data) as APersistentMap)
    UIUtil.invokeLaterIfNeeded {
      val model = getModel(client)
      model.transaction { m ->
        model.dispatch(action, m)
      }
    }
  }

  server.addDisconnectListener(object : DisconnectListener {
    override fun onDisconnect(client: SocketIOClient) {
      //      // Persist model?
      //      UIUtil.invokeLaterIfNeeded {
      //        val model = getModel(client)
      //        model.lifetime.terminate()
      //      }
    }
  });

  server.start()

  lifetime += {
    server.stop()
  }
}

fun toModel(obj: Any?): Model =
    when (obj) {
      is Map<*, *> -> MapModel(obj.entrySet().map { it.key!! to toModel(it.value) }.toMap())
      is List<*> -> ListModel(obj.map { toModel(it) })
      null -> AbsentModel()
      else -> PrimitiveModel(obj)
    }


private fun createModel(lifetime: Lifetime, clientUUID: UUID, server: SocketIOServer): ReactiveModel {
  val reactiveModel = ReactiveModel(lifetime, { diff ->
    val data = toClojure(diff)

    val client = server.getClient(clientUUID)
    // client may be disconnected
    client?.sendEvent("diff", RT.printString(data))
  })
  return reactiveModel
}

val type = "@@@--^type"
val typeKeyword = !"dtype"

private fun fromModel(obj: Model): Any? =
    when (obj) {
      is MapModel -> PersistentHashMap.create((obj.hmap as Map<Any, Model> ).map { it.key to fromModel(it.value) }.toMap())
      is ListModel -> PersistentVector.create(obj.list.map { fromModel(it) })
      is PrimitiveModel<*> -> obj.value
      is AbsentModel -> null
      else -> throw AssertionError("Unknown model object $obj")
    }


fun toClojure(diff: Diff<*>): APersistentMap =
    diff.acceptVisitor(object : DiffVisitor<APersistentMap> {
      override fun visitPrimitiveDiff(primitiveDiff: PrimitiveDiff) = PersistentArrayMap.createAsIfByAssoc(arrayOf(
          typeKeyword, !"primitive-diff",
          !"new-value", primitiveDiff.newValue
      ))

      override fun visitListDiff(listDiff: ListDiff) = PersistentArrayMap.createAsIfByAssoc(arrayOf(
          typeKeyword, !"list-diff",
          !"index", listDiff.index,
          !"list", PersistentList.create(listDiff.nueu.map { fromModel(it) })
      ))

      override fun visitMapDiff(mapDiff: MapDiff) = PersistentHashMap.create(*mapDiff.diff.map { entry: Map.Entry<Any, Diff<Model>> ->
        entry.getKey() to toClojure(entry.getValue())
      }.plus(typeKeyword to !"map-diff").flatMap { arrayListOf(it.first, it.second) }.toTypedArray())

      override fun visitValueDiff(valueDiff: ValueDiff<*>) = PersistentArrayMap.createAsIfByAssoc(arrayOf(
          typeKeyword, !"value-diff",
          !"new-value", fromModel(valueDiff.newValue)
      ))
    })


fun toDiff(edn: APersistentMap): Diff<Model> =
    when (edn[typeKeyword]) {
      !"primitive-diff" -> {
        PrimitiveDiff(edn.get(!"new-value"))
      }
      !"list-diff" -> ListDiff((edn[!"list"] as List<*>).map { toModel(it) }, Integer.parseInt(edn[!"index"].toString()))
      !"map-diff" -> MapDiff((edn as Map<Any, APersistentMap>).filter { e -> e.getKey() != typeKeyword }
          .map { entry -> entry.getKey() to toDiff(entry.getValue()) }.toMap())
      !"value-diff" -> {
        val newValue = edn.get(!"new-value")
        ValueDiff(if (newValue == null) AbsentModel() else toModel(newValue))
      }
      else -> throw AssertionError("unknown diff type\n$edn")
    }


fun main(args: Array<String>) {
  val port = 12345
  //  val server = serverModel(Lifetime.Eternal, port, { model ->
  //    model.transaction { m ->
  //      Path("a").putIn(m, PrimitiveModel("abcd"))
  //    }
  //  })

  //  val clientModel = clientModel("http://localhost:" + port, Lifetime.Eternal)
}

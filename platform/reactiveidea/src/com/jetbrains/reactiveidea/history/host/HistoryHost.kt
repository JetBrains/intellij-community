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
package com.jetbrains.reactiveidea.history.host

import com.github.krukow.clj_lang.IPersistentMap
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.createMeta
import com.jetbrains.reactivemodel.util.get
import com.jetbrains.reactivemodel.util.host
import java.lang.reflect.Type
import java.util.*

public class HistoryHost(val reactiveModel: ReactiveModel,
                         val path: Path,
                         val lifetime: Lifetime,
                         val vfManager: VirtualFileManager,
                         init: Initializer) : Host {
  init {
    val gson = GsonBuilder()
        .registerTypeAdapter(javaClass<PlaceInfo>(), object : JsonSerializer<PlaceInfo>, JsonDeserializer<PlaceInfo> {
          override fun serialize(src: PlaceInfo, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()
            jsonObject.add("file", context.serialize(src.getFile().getUrl()))
            jsonObject.add("state", context.serialize(src.getNavigationState()))
            jsonObject.add("editorId", context.serialize(src.getEditorTypeId()))
            return jsonObject
          }

          override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): PlaceInfo {
            val obj = json.getAsJsonObject()
            val url = obj["file"].getAsString()
            val file = vfManager.refreshAndFindFileByUrl(url)
            val state: FileEditorState = context.deserialize(obj["state"], javaClass<TextEditorState>())
            return PlaceInfo(file, state, obj["editorId"].getAsString(), null)
          }
        })
        .create()

    val listPath = path / "list"
    val headPath = path / "head"
    init += { m ->
      // nothing now cause it not persistent
      val headModel = m.getIn(headPath) as? PrimitiveModel<*>
      if(headModel != null) {
        head = (headModel.value as String).toInt()
      }
      val listModel = m.getIn(listPath) as? PrimitiveModel<*>
      if(listModel != null) {
        list = gson.fromJson(listModel.value as String, object: TypeToken<ArrayList<PlaceInfo>>(){}.getType())
      }
      m
    }
    lifetime += {
      reactiveModel.transaction { m ->
        var model = m
        model = listPath.putIn(model, PrimitiveModel(gson.toJson(list)))
        val head = head;
        if(head != null) {
          model = headPath.putIn(model, PrimitiveModel(head))
        }
        model
      }
    }
  }

  var list: List<PlaceInfo> = emptyList()
  var head: Int? = null
}


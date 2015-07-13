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

import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.util.get
import java.awt.Component

/**
 * Server data manager. Perform lookup through model hierarchy
 */
public class ServerDataManagerImpl : DataManagerImpl() {
  override fun getDataFromProvider(provider: DataProvider, dataId: String, alreadyComputedIds: MutableSet<String>?): Any? {
    var dataPath = extractPath(provider)
    if (dataPath is Path) {
      var path: Path = dataPath
      while (true) {
        val model: Model = path.getIn(ReactiveModel.current()!!.root) ?: break
        val data = model.meta[dataId]
        if(data != null) {
          return data
        }
        if(path.components.isEmpty()) {
          break;
        }
        path = path.dropLast(1)
      }
    }
    return super.getDataFromProvider(provider, dataId, alreadyComputedIds)
  }

  private fun extractPath(provider: DataProvider): Any? {
    var dataPath = provider.getData(pathKey.toString())
    if(dataPath == null && provider is EditorComponentImpl) {
      dataPath = provider.getEditor().getUserData(pathKey)
    }
    return dataPath
  }
}
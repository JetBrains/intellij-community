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

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.getIn
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.util.host

public val historyPath: Path = Path("history")

public fun historyHost(m: MapModel): HistoryHost {
  val host = m.getIn(historyPath)!!.meta.host<HistoryHost>()
  return host
}

public fun getHead(m: MapModel): Int? {
  return historyHost(m).head;
}

public fun getHistoryList(m: MapModel): List<IdeDocumentHistoryImpl.PlaceInfo> {
  return historyHost(m).list;
}

public fun back(m: MapModel): MapModel {
  val head = getHead(m) ?: throw IllegalStateException("Head haven't previous state")
  val newHead = head - 1
  return setHead(m, if (newHead >= 0) newHead else null)
}

public fun forward(m: MapModel): MapModel {
  val head = getHead(m) ?: -1
  val currentList = getHistoryList(m)
  if ((head + 1) >= currentList.size()) {
    throw IllegalStateException("Can't move head ahead of list")
  }
  return setHead(m, head + 1)
}

public fun addCurPlace(m: MapModel, curInfo: IdeDocumentHistoryImpl.PlaceInfo): MapModel {
  val head = getHead(m) ?: -1
  val current = head + 1
  return updateList(m) { list ->
    list.subList(0, current).plus(curInfo)
  }
}

public fun currentPlace(m: MapModel): IdeDocumentHistoryImpl.PlaceInfo? {
  val head = getHead(m) ?: -1
  val list = getHistoryList(m)
  if (head + 1 < list.size()) {
    return list[head + 1];
  }
  return null
}

public fun ensureSize(m: MapModel, size: Int): MapModel {
  val history = getHistoryList(m)
  val skip = history.size() - size
  if (skip > 0) {
    val head = getHead(m)
    var model = m;
    if (head != null) {
      val res = head - skip
      model = setHead(m, nullOrNotNegative(res))
    }
    return updateList(model) { list ->
      list.subList(skip, list.size())
    }
  }
  return m
}

public fun removeInvalidInfos(m: MapModel): MapModel {
  val head = getHead(m)
  if (head != null) {
    val history = getHistoryList(m)
    var res = 0;
    history.forEachIndexed { i, placeInfo ->
      if (!placeInfo.getFile().isValid() && head >= i) {
        res++
      }
    }
    setHead(m, nullOrNotNegative(head - res))
  }
  return updateList(m) { list -> list.filter { it.getFile().isValid() } }
}

private fun nullOrNotNegative(value: Int): Int? = if (value < 0) null else value

private fun setHead(m: MapModel, value: Int?): MapModel {
  historyHost(m).head = value;
  return m
}

private fun updateList(m: MapModel, f: (List<IdeDocumentHistoryImpl.PlaceInfo>) -> List<IdeDocumentHistoryImpl.PlaceInfo>): MapModel {
  val host = historyHost(m)
  host.list = f(host.list)
  return m
}
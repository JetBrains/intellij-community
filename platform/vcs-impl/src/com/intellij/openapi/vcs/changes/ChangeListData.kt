// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl
import org.jdom.Element
import java.util.*
import kotlin.collections.ArrayList


private const val CHANGELIST_DATA: String = "changelist_data"

open class ChangeListData @JvmOverloads constructor(val author: VcsUser? = null, val date: Date? = null) {

  private constructor(state: State) : this(VcsUserImpl(state.name ?: "", state.email ?: ""), state.date)

  private var myState: State = State(author?.name, author?.email, date)

  fun getPresentation(): String {
    val lines = ArrayList<String>()
    author?.let { lines.add("Author: $author") }
    date?.let { lines.add("Date: ${DateFormatUtil.formatDateTime(date)}") }
    return StringUtil.join(lines, "\n")
  }

  @Tag(CHANGELIST_DATA)
  inner class State @JvmOverloads constructor(@Attribute val name: String? = null, @Attribute val email: String? = null, @Attribute val date: Date? = null)

  companion object {
    @JvmStatic
    fun writeExternal(listData: ChangeListData): Element = XmlSerializer.serialize(listData.myState)

    @JvmStatic
    fun readExternal(parent: Element): ChangeListData? {
      val element = parent.getChild(CHANGELIST_DATA)
      return if (element != null) ChangeListData(XmlSerializer.deserialize(element, State::class.java)) else null
    }
  }
}
                                                              
                                                              
                                                              
                                                              
                                                              

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.xml.util.XmlStringUtil
import org.jdom.Element
import java.util.*
import kotlin.collections.ArrayList


private const val CHANGELIST_DATA: String = "changelist_data" // NON-NLS

val LocalChangeList.changeListData: ChangeListData? get() = (data as? ChangeListData)?.nullize()
val LocalChangeList.author: VcsUser? get() = changeListData?.author
val LocalChangeList.authorDate: Date? get() = changeListData?.date

data class ChangeListData @JvmOverloads constructor(val author: VcsUser? = null, val date: Date? = null) {

  private constructor(state: State) : this(VcsUserImpl(state.name ?: "", state.email ?: ""), state.date)

  private var myState: State = State(author?.name, author?.email, date)

  fun nullize(): ChangeListData? = if (author == null && date == null) null else this

  fun getPresentation(): String {
    val lines = ArrayList<String>()
    author?.let {
      lines.add(VcsBundle.message("commit.description.tooltip.author", XmlStringUtil.escapeString(author.toString())))
    }
    date?.let {
      lines.add(VcsBundle.message("commit.description.tooltip.date", XmlStringUtil.escapeString(DateFormatUtil.formatDateTime(date))))
    }
    return StringUtil.join(lines, UIUtil.BR)
  }

  @Tag(CHANGELIST_DATA)
  class State @JvmOverloads constructor(@Attribute("name") var name: String? = null,
                                        @Attribute("email") var email: String? = null,
                                        @Attribute("date") var date: Date? = null)

  companion object {
    fun of(author: VcsUser?, date: Date?): ChangeListData? =
      if (author == null && date == null) null else ChangeListData(author, date)

    @JvmStatic
    fun writeExternal(listData: ChangeListData): Element = XmlSerializer.serialize(listData.myState)

    @JvmStatic
    fun readExternal(parent: Element): ChangeListData? {
      val element = parent.getChild(CHANGELIST_DATA)
      return if (element != null) ChangeListData(XmlSerializer.deserialize(element, State::class.java)) else null
    }
  }
}
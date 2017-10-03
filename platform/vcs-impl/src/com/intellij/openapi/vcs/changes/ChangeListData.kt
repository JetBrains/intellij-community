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
import com.intellij.vcs.log.VcsUser
import java.util.*
import kotlin.collections.ArrayList

open class ChangeListData @JvmOverloads constructor(val author: VcsUser? = null, val date: Date? = null) {
  fun getPresentation(): String {
    val lines = ArrayList<String>()
    author?.let { lines.add("Author: $author") }
    date?.let { lines.add("Date: ${DateFormatUtil.formatDateTime(date)}") }
    return StringUtil.join(lines, "\n")
  }
}
                                                              
                                                              
                                                              
                                                              
                                                              

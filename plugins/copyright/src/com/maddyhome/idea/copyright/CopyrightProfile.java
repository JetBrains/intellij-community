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
package com.maddyhome.idea.copyright

import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.ProfileEx
import com.intellij.util.xmlb.SmartSerializer
import com.maddyhome.idea.copyright.pattern.EntityUtil

class CopyrightProfile @JvmOverloads constructor(profileName: String = "") : ProfileEx(profileName, SmartSerializer()) {
  companion object {
    @JvmField
    val DEFAULT_COPYRIGHT_NOTICE: String = EntityUtil.encode(
      "Copyright (c) \$today.year. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n" +
      "Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan. \n" +
      "Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna. \n" +
      "Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus. \n" +
      "Vestibulum commodo. Ut rhoncus gravida arcu. ")
  }

  var notice = DEFAULT_COPYRIGHT_NOTICE
  var keyword = EntityUtil.encode("Copyright")

  var allowReplaceRegexp: String? = null
    set(allowReplaceRegexp) {
      field = StringUtil.nullize(allowReplaceRegexp)
    }
}//read external

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright

import com.maddyhome.idea.copyright.pattern.DateInfo
import com.maddyhome.idea.copyright.pattern.VelocityHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CommentInfoTest {
  val template = "Copyright (c) \$originalComment.match(\"Copyright \\(c\\) (\\d+)\", 1, \" - \", \"\$today.year\")\$today.year. " +
                 "Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"
  
  @Test
  fun noComment() {
    val dateInfo = DateInfo().year
    Assertions.assertEquals("Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                            VelocityHelper.evaluate (null, null, null, template, null))
  }

  @Test
  fun withAnotherComment() {
    val dateInfo = DateInfo().year
    Assertions.assertEquals("Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                            VelocityHelper.evaluate (null, null, null, template,
                                                     "Copyright (c). Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }

  @Test
  fun withComment() {
    val dateInfo = DateInfo().year
    Assertions.assertEquals("Copyright (c) 2020 - $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                            VelocityHelper.evaluate (null, null, null, template,
                                                     "Copyright (c) 2020. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }
  
  @Test
  fun withCommentOldYears() {
    val dateInfo = DateInfo().year
    Assertions.assertEquals("Copyright (c) 2019 - $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                            VelocityHelper.evaluate (null, null, null, template,
                                                     "Copyright (c) 2019 - 2020. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }
  
  @Test
  fun withCommentSameYear() {
    val dateInfo = DateInfo().year
    Assertions.assertEquals("Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                            VelocityHelper.evaluate (null, null, null, template,
                                                     "Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }
}
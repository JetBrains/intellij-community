// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright

import com.maddyhome.idea.copyright.pattern.DateInfo
import com.maddyhome.idea.copyright.pattern.VelocityHelper
import org.junit.Assert
import org.junit.Test

class CommentInfoTest {
  val template = "Copyright (c) \$originalComment.match(\"Copyright \\(c\\) (\\d+)\", 1, \" - \")\$today.year. " +
                 "Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"
  
  @Test
  fun noComment() {
    val dateInfo = DateInfo().year
    Assert.assertEquals("Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                        VelocityHelper.evaluate (null, null, null, template, null))
  }

  @Test
  fun withAnotherComment() {
    val dateInfo = DateInfo().year
    Assert.assertEquals("Copyright (c) $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                        VelocityHelper.evaluate (null, null, null, template,
                                                 "Copyright (c). Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }

  @Test
  fun withComment() {
    val dateInfo = DateInfo().year
    Assert.assertEquals("Copyright (c) 2020 - $dateInfo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n",
                        VelocityHelper.evaluate (null, null, null, template, 
                                                 "Copyright (c) 2020. Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n"))
  }
}
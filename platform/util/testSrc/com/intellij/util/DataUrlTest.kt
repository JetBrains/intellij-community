// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class DataUrlTest {

  /**
   * Examples from https://www.ietf.org/rfc/rfc2397.html
   */
  @OptIn(ExperimentalStdlibApi::class)
  @Test
  fun dataUrlParseTests() {
    assertThat(DataUrl.parse("data:,A%20brief%20note"))
      .extracting("data", "contentType", "params")
      .containsExactly("A brief note".toByteArray(), "text/plain", listOf("charset=US-ASCII"))


    val expectedGifData =
      ("47494638376130003000f00000000000ffffff2c00000000300030000002f08c8fa9cbeddf009c0e488b73b0b4ab0c861e1496a6342e" +
       "e72aa6098b1aa72baf51496f38d98e66d7f3805cc1c93075d10831391d13a8141e8e144dcce7e4549fb962252aad717963994a87f0de" +
       "b8d70fe722d61ac11bb4b88e4a96bf4cf83b269247c727a777359305f5f47313a728f8e00738b87628a758676417c92375f9f2710657" +
       "65e6067a398995978697d8b689c56a68d55a8a54fa179889f749ba9afb934b838ad39334bc39344385d4b70bca3b3adc7778aaf6e8ac" +
       "17cd4cd4e21c47b9447b0c1c2e9eedb8d364337b8d9dadc5d89405a3ded5ae2cbf5e5e3f7f571fe555b35f2ca6ecf1e1871e1d6e060a" +
       "00003b").hexToByteArray()

    assertThat(DataUrl.parse("data:image/gif;base64,R0lGODdhMAAwAPAAAAAAAP///ywAAAAAMAAw" +
                             "AAAC8IyPqcvt3wCcDkiLc7C0qwyGHhSWpjQu5yqmCYsapyuvUUlvONmOZtfzgFz" +
                             "ByTB10QgxOR0TqBQejhRNzOfkVJ+5YiUqrXF5Y5lKh/DeuNcP5yLWGsEbtLiOSp" +
                             "a/TPg7JpJHxyendzWTBfX0cxOnKPjgBzi4diinWGdkF8kjdfnycQZXZeYGejmJl" +
                             "ZeGl9i2icVqaNVailT6F5iJ90m6mvuTS4OK05M0vDk0Q4XUtwvKOzrcd3iq9uis" +
                             "F81M1OIcR7lEewwcLp7tuNNkM3uNna3F2JQFo97Vriy/Xl4/f1cf5VWzXyym7PH" +
                             "hhx4dbgYKAAA7"))
      .extracting("data", "contentType", "params")
      .containsExactly(expectedGifData, "image/gif", emptyList<String>())


    assertThat(DataUrl.parse("data:text/plain;charset=iso-8859-7,%be%fe%be"))
      .extracting("data", "contentType", "params")
      .containsExactly("ΎώΎ".toByteArray(Charset.forName("iso-8859-7")), "text/plain", listOf("charset=iso-8859-7"))
  }


}
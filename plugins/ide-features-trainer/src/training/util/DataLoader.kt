// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import org.jdom.Document
import org.jdom.Element
import org.jdom.input.SAXBuilder
import java.io.FileInputStream
import java.io.InputStream

object DataLoader {
  private const val DATA_PATH = "data/"
  //Path to use for online reloading. Should contain the full path to res/ folder, like /Users/user/training/res/.
  private const val LIVE_DATA_PATH = ""

  val liveMode: Boolean
    get() {
      return LIVE_DATA_PATH.isNotEmpty()
    }

  fun getResourceAsStream(pathFromData: String, classLoader: ClassLoader): InputStream {
    val fullPath =
      LIVE_DATA_PATH + DATA_PATH + pathFromData
    return if (liveMode) {
      FileInputStream(fullPath)
    }
    else {
      classLoader.getResourceAsStream(fullPath) ?: throw Exception("File with \"$pathFromData\" doesn't exist")
    }
  }

  private fun getXmlDocument(pathFromData: String, classLoader: ClassLoader): Document {
    val resourceStream = getResourceAsStream(pathFromData, classLoader)
    val builder = SAXBuilder()
    return builder.build(resourceStream) ?: throw Exception("Unable to get document for xml: $pathFromData")
  }

  fun getXmlRootElement(pathFromData: String, classLoader: ClassLoader): Element {
    return getXmlDocument(pathFromData, classLoader).rootElement
  }
}

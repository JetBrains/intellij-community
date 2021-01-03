// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import org.jdom.Element
import training.util.DataLoader

class Scenario(private val path: String, classLoader: ClassLoader) {

  val root: Element = DataLoader.getXmlRootElement(path, classLoader)

  val lang: String
    get() {
      val lang = root.getAttribute(LANG)
      if (lang != null) return lang.value else throw Exception("Cannot get '$LANG' property for the lesson file with path: $path")
    }

  val name: String
    get() = root.getAttribute(NAME)!!.value

  /**
   * It is a unique String attribute to distinguish different lessons with a probably similar names
   */
  val id: String
    get() = root.getAttribute(ID)!!.value

  val file: String?
    get() = root.getAttribute(FILE)?.value

  companion object {
    private const val LANG = "lang"
    private const val NAME = "name"
    private const val ID = "id"
    private const val FILE = "file"
  }

}

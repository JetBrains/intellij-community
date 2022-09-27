package org.jetbrains.completion.full.line.local

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer

fun encodeToXml(obj: Any): String {
  val el = XmlSerializer.serialize(obj)
  return JDOMUtil.write(el)
}

inline fun <reified T> decodeFromXml(xml: String): T {
  val a = JDOMUtil.load(xml)
  return XmlSerializer.deserialize(a, T::class.java)
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeStAXStreamBuilder
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class CborXmlTest {
  @Test
  fun simpleXml() {
    @Language("xml")
    val xml = """
      <root>
        <those>
          <doing>
            <women>-147057817</women>
            <birthday>-45780313</birthday>
            <environment>1078918263.8264647</environment>
            <iron>-1327827149.4260724</iron>
            <mysterious>-1962454268</mysterious>
            <human>-7606928</human>
          </doing>
          <volume>
            <slave>1974313942.2899964</slave>
            <space>gun</space>
            <knife>-1959253947.7912233</knife>
            <mouth>voyage</mouth>
            <simple>near</simple>
            <tonight>phrase</tonight>
          </volume>
          <depend>smooth</depend>
          <young>toy</young>
          <art>white</art>
          <stick>550769328</stick>
        </those>
        <typical>toward</typical>
        <won>underline</won>
        <dug>smaller</dug>
        <tape>-1268694563</tape>
        <system>toy</system>
      </root>
    """.trimIndent()
    val encoded = encodeXmlToCbor(JDOMUtil.load(xml))
    val decoded = decodeCborToXml(encoded)
    assertThat(JDOMUtil.write(decoded)).isEqualTo(xml)
  }

  @Test
  fun complex() {
    @Language("xml")
    val xml = """
      <root>
        <older>
          <usually related="heart">1862233802</usually>
          <ahead anyway="slowly">
            <![CDATA[living ordinary donkey whispered till pattern blew broke]]>
            <palace wore="lost">gone</palace>
            <will>push</will>
            <blind>useful</blind>
            <adult>electricity</adult>
            <remain war="disappear">-1556962708.4832356</remain>
            <outside third="hurt">afternoon</outside>
          </ahead>
          <behind>came</behind>
          <center><![CDATA[twice change]]>702899409</center>
          <east flight="palace">spider</east>
          <account season="gone">-543519700.9329865 <![CDATA[driver]]></account>
        </older>
        <smaller measure="get">layers</smaller>
        <original atom="jet">-1722888163.410596</original>
        <![CDATA[hold seven]]><![CDATA[just cloud]]>
        <thy>layers</thy>
        <chose enemy="voyage">1521238452.9970212</chose>
        <reason>1787147972.9810781</reason>
      </root>
    """.trimIndent()

    // non-coalescing - test CDATA
    val xmlStreamReader = createNonCoalescingXmlStreamReader(xml.encodeToByteArray(), null)
    val jdom = try {
      SafeStAXStreamBuilder.build(xmlStreamReader, true, true, SafeStAXStreamBuilder.FACTORY)
    }
    finally {
      xmlStreamReader.close()
    }

    val encoded = encodeXmlToCbor(jdom)
    val decoded = decodeCborToXml(encoded)
    assertThat(JDOMUtil.write(decoded)).isEqualTo(xml)
  }
}
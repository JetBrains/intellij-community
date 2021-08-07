// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.util.StaxFactory;
import com.intellij.util.NoOpXmlInterner;
import com.intellij.util.XmlDomReader;
import org.codehaus.stax2.XMLStreamReader2;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationInfoTest {
  @Test
  public void shortenCompanyName() throws Exception {
    assertShortCompanyName("<component><company name='Google Inc.'/></component>", "Google");
    assertShortCompanyName("<component><company name='JetBrains s.r.o.'/></component>", "JetBrains");
    assertShortCompanyName("<component><company shortName='Acme Inc.'/></component>", "Acme Inc.");
  }

  private static void assertShortCompanyName(@Language("XML") String xml, String expected)
    throws XMLStreamException {
    XMLStreamReader2 reader = StaxFactory.createXmlStreamReader(new StringReader(xml));
    reader.nextTag();
    ApplicationInfoImpl info = new ApplicationInfoImpl(XmlDomReader.readXmlAsModel(reader, null, NoOpXmlInterner.INSTANCE));
    assertThat(info.getShortCompanyName()).isEqualTo(expected);
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.options.SchemeImportException;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

/**
 * Reads settings from Eclipse XML profile. The actual work is done by an implementor of <code>handleOption()</code> method.
 * 
 * @author Rustam Vishnyakov
 */
public class EclipseXmlProfileReader extends DefaultHandler implements EclipseXmlProfileElements {

  private final OptionHandler myOptionHandler;

  protected EclipseXmlProfileReader(OptionHandler optionHandler) {
    myOptionHandler = optionHandler;
  }

  /**
   * Reads either basic profile info (name) or all the settings depending on whether <code>settings</code> parameter is null.
   * 
   * @param input The input stream to read from.
   * @throws SchemeImportException
   */
  protected void readSettings(InputStream input) throws SchemeImportException {
    SAXParserFactory spf = SAXParserFactory.newDefaultInstance();
    spf.setValidating(false);
    SAXParser parser;
    try {
      parser = spf.newSAXParser();
      parser.parse(input, this);
    }
    catch (Exception e) {
      if (e.getCause() instanceof NonEclipseXmlFileException) {
        throw new SchemeImportException("The input file is not a valid Eclipse XML profile.");
      }
      else {
        throw new SchemeImportException(e);
      }
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (PROFILE_TAG.equals(qName)) {
      myOptionHandler.handleName(attributes.getValue(NAME_ATTR));
    }
    else if (SETTING_TAG.equals(qName)) {
      String key = attributes.getValue(ID_ATTR);
      String value = attributes.getValue(VALUE_ATTR);
      if (key != null && value != null) {
        try {
          myOptionHandler.handleOption(key, value);
        }
        catch (SchemeImportException e) {
          throw new SAXException(e);
        }
      }
    }
    else if (PROFILES_TAG.equals(qName)) {
      // Ignore
    }
    else {
      throw new SAXException(new NonEclipseXmlFileException("Unknown XML element: " + qName));
    }
  }
  
  private static class NonEclipseXmlFileException extends Exception {
    NonEclipseXmlFileException(String message) {
      super(message);
    }
  }


  interface OptionHandler {
    void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException;
    void handleName(String name);
  }
}

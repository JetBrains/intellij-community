/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  private OptionHandler myOptionHandler;

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
    SAXParserFactory spf = SAXParserFactory.newInstance();
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
    else //noinspection StatementWithEmptyBody
      if (PROFILES_TAG.equals(qName)) {
      // Ignore
    }
    else {
      throw new SAXException(new NonEclipseXmlFileException("Unknown XML element: " + qName));
    }
  }
  
  private static class NonEclipseXmlFileException extends Exception {
    public NonEclipseXmlFileException(String message) {
      super(message);
    }
  }


  interface OptionHandler {
    void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException;
    void handleName(String name);
  }
}

/*--

 Copyright (C) 2011 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.input.sax;

import org.jdom.JDOMException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * An enumeration of {@link XMLReaderJDOMFactory} that allows for a single
 * central location to create XMLReaders. The Singletons (members) of this
 * enumeration can produce the most common XMLReaders: non-validating, XSD
 * validating, and DocType validating.
 * <p>
 * See the {@link org.jdom.input.sax package documentation} for details of how
 * to create the SAXParser you desire.
 *
 * @author Rolf Lear
 * @see org.jdom.input.sax
 */
public enum XMLReaders implements XMLReaderJDOMFactory {

  /**
   * The non-validating singleton
   */
  NONVALIDATING(0),

  /**
   * The DTD-validating Singleton
   */
  DTDVALIDATING(1);

  private interface FactorySupplier {
    SAXParserFactory supply() throws Exception;

    boolean validates();
  }

  private enum NONSingleton implements FactorySupplier {
    INSTANCE;

    private final SAXParserFactory factory;

    NONSingleton() {
      SAXParserFactory fac = SAXParserFactory.newInstance();
      // All JDOM parsers are namespace aware.
      fac.setNamespaceAware(true);
      fac.setValidating(false);
      factory = fac;
    }

    @Override
    public SAXParserFactory supply() {
      return factory;
    }

    @Override
    public boolean validates() {
      return false;
    }
  }

  private enum DTDSingleton implements FactorySupplier {
    INSTANCE;

    private final SAXParserFactory factory;

    DTDSingleton() {
      SAXParserFactory fac = SAXParserFactory.newInstance();
      // All JDOM parsers are namespace aware.
      fac.setNamespaceAware(true);
      // factory is validating (DTD)
      fac.setValidating(true);
      factory = fac;
    }

    @Override
    public SAXParserFactory supply() {
      return factory;
    }

    @Override
    public boolean validates() {
      return true;
    }
  }

  private enum XSDSingleton implements FactorySupplier {
    INSTANCE;

    private final Exception failcause;
    private final SAXParserFactory factory;

    XSDSingleton() {

      SAXParserFactory fac = SAXParserFactory.newInstance();
      Exception problem = null;
      // All JDOM parsers are namespace aware.
      fac.setNamespaceAware(true);
      // factory is not validating (DTD), but the Reader is validating
      // (XSD)
      fac.setValidating(false);

      try {
        SchemaFactory sfac = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sfac.newSchema();
        fac.setSchema(schema);
      }
      catch (SAXException se) {
        // we could not get a validating system, set the fac to null
        fac = null;
        problem = se;
      }
      catch (IllegalArgumentException iae) {
        // this system does not support XSD Validation.... which is true
        // for android!
        // we could not get a validating system, set the fac to null
        fac = null;
        problem = iae;
      }
      catch (UnsupportedOperationException uoe) {
        // SAXParserFactory throws this exception when setSchema is
        // called.
        // Therefore every factory throws this exception unless it
        // overrides
        // setSchema. A popular example is Apache Xerces
        // SAXParserFactoryImpl
        // before version 2.7.0.
        fac = null;
        problem = uoe;
      }
      factory = fac;
      failcause = problem;
    }

    @Override
    public SAXParserFactory supply() throws Exception {
      if (factory == null) {
        throw failcause;
      }
      return factory;
    }

    @Override
    public boolean validates() {
      return true;
    }
  }

  /**
   * the actual SAXParserFactory in the respective singletons.
   */
  private final int singletonID;

  /**
   * Private constructor
   */
  XMLReaders(int singletonID) {
    this.singletonID = singletonID;
  }

  private FactorySupplier getSupplier() {
    switch (singletonID) {
      case 0:
        return NONSingleton.INSTANCE;
      case 1:
        return DTDSingleton.INSTANCE;
      case 2:
        return XSDSingleton.INSTANCE;
    }
    throw new IllegalStateException("Unknown singletonID: " + singletonID);
  }

  /**
   * Get a new XMLReader from this JAXP-based {@link XMLReaderJDOMFactory}.
   * <p>
   *
   * @return a new XMLReader instance.
   * @throws JDOMException if there is a problem creating the XMLReader
   */
  @Override
  public XMLReader createXMLReader() throws JDOMException {
    try {
      FactorySupplier supplier = getSupplier();
      return supplier.supply().newSAXParser().getXMLReader();
    }
    catch (SAXException | ParserConfigurationException e) {
      throw new JDOMException(
        "Unable to create a new XMLReader instance", e);
    }
    catch (Exception e) {
      throw new JDOMException("It was not possible to configure a suitable XMLReader to support " + this, e);
    }
  }

  @Override
  public boolean isValidating() {
    return getSupplier().validates();
  }
}

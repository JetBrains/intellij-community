/*-- 

 Copyright (C) 2011-2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom;

import org.jdom.xpath.XPathFactory;

/**
 * A collection of constants that may be useful to JDOM users.
 * <p>
 * JDOM attempts to make knowing these 'magic' constants unnecessary, but it is
 * not always possible. In an attempt to make it easier though, common constants
 * are defined here. This is not a comprehensive list of all the constants that
 * may be useful when processing XML, but it should cover most of those occasions
 * where JDOM does not automatically do the 'right thing'.
 * <p>
 * Many of these constants are already referenced inside the JDOM code.
 *
 * @author Rolf Lear
 */
@SuppressWarnings("HttpUrlsUsage")
public final class JDOMConstants {

  /**
   * Keep out of public reach.
   */
  private JDOMConstants() {
    // private default constructor.
  }

  /*
   * XML Namespace constants
   */

  /**
   * Defined as {@value}
   */
  public static final String NS_PREFIX_DEFAULT = "";
  /**
   * Defined as {@value}
   */
  public static final String NS_URI_DEFAULT = "";

  /**
   * Defined as {@value}
   */
  static final String NS_PREFIX_XML = "xml";
  /**
   * Defined as {@value}
   */
  static final String NS_URI_XML = "http://www.w3.org/XML/1998/namespace";

  /**
   * Defined as {@value}
   */
  public static final String NS_PREFIX_XMLNS = "xmlns";
  /**
   * Defined as {@value}
   */
  public static final String NS_URI_XMLNS = "http://www.w3.org/2000/xmlns/";

  /**
   * Defined as {@value}
   */
  public static final String SAX_PROPERTY_DECLARATION_HANDLER = "http://xml.org/sax/properties/declaration-handler";

  /**
   * Defined as {@value}
   */
  public static final String SAX_PROPERTY_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";

  /**
   * Defined as {@value}
   */
  public static final String SAX_PROPERTY_LEXICAL_HANDLER_ALT = "http://xml.org/sax/handlers/LexicalHandler";

  /**
   * System Property queried to obtain an alternate default XPathFactory.
   * Defined as {@value}
   *
   * @see XPathFactory
   */
  public static final String JDOM2_PROPERTY_XPATH_FACTORY = "org.jdom.xpath.XPathFactory";
}

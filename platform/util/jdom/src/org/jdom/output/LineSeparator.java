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

package org.jdom.output;

import org.jdom.JDOMConstants;
import org.jdom.internal.SystemProperty;

/**
 * An enumeration of common separators that are used for JDOM output.
 * <p>
 * These enumerated values can be used as input to the
 * {@link Format#setLineSeparator(LineSeparator)} method. Additionally, the
 * names of these constants can be also be used in the System Property
 * {@link JDOMConstants#JDOM2_PROPERTY_LINE_SEPARATOR} which is used to
 * define the default Line Separator sequence for JDOM output. See
 * {@link #DEFAULT} Javadoc.
 *
 * <p>
 * JDOM has historically used the CR/NL sequence '\r\n' as a line-terminator.
 * This sequence has the advantage that the output is easily opened in the
 * 'Notepad' editor on Windows. Other editors on other platforms are typically
 * smart enough to automatically adjust to whatever termination sequence is
 * used in the document. The XML specification requires that the CR/NL sequence
 * should be 'normalized' to a single newline '\n' when the document is parsed
 * (<a href="https://www.w3.org/TR/xml11/#sec-line-ends">XML 1.1 End-Of-Line
 * Handling</a>). As a result there is no XML issue with the JDOM default CR/NL
 * end-of-line sequence.
 * <p>
 * It should be noted that because JDOM internally stores just a '\n' as a line
 * separator that any other output separator requires additional processing to
 * output. There is a distinct performance benefit for using the UNIX, or NL
 * LineSeparator for output.
 * <p>
 * JDOM has always allowed the line-terminating sequence to be customised (or
 * even disabled) for each {@link XMLOutputter2} operation by using this Format
 * class.
 * <p>
 * JDOM2 introduces two new features in relation to the end-of-line sequence.
 * Firstly, it introduces this new {@link LineSeparator} enumeration which
 * formalises the common line separators that can be used. In addition to the
 * simple String-based {@link Format#setLineSeparator(String)} method you can
 * now also call {@link Format#setLineSeparator(LineSeparator)} with one of the
 * common enumerations.
 * <p>
 * The second new JDOM2 feature is the ability to set a global default
 * end-of-line sequence. JDOM 1.x forced the default sequence to be the CRLF
 * sequence, but JDOM2 allows you to set the system property
 * {@link JDOMConstants#JDOM2_PROPERTY_LINE_SEPARATOR} which will be used as the
 * default sequence for Format. You can set the property to be the name of one
 * of these LineSeparator enumerations too. For example, the following will
 * cause all default Format instances to use the System-dependent end-of-line
 * sequence instead of always CRLF:
 * <p>
 * <pre>
 * java -Dorg.jdom.output.LineSeparator=SYSTEM ...
 * </pre>
 *
 * @author Rolf Lear
 * @since JDOM2
 */
public enum LineSeparator {
  /**
   * The Separator sequence CRNL which is '\r\n'.
   * This is the default sequence.
   */
  CRNL("\r\n"),

  /**
   * The Separator sequence NL which is '\n'.
   */
  NL("\n"),
  /**
   * The Separator sequence CR which is '\r'.
   */
  CR("\r"),

  /**
   * The 'DOS' Separator sequence CRLF (CRNL) which is '\r\n'.
   */
  DOS("\r\n"),

  /**
   * The 'UNIX' Separator sequence NL which is '\n'.
   */
  UNIX("\n"),

  /**
   * Perform no end-of-line processing.
   */
  NONE(null),

  /**
   * Use the sequence '\r\n' unless the System property
   * {@link JDOMConstants#JDOM2_PROPERTY_LINE_SEPARATOR} is defined, in which
   * case use the value specified in that property. If the value in that
   * property matches one of the Enumeration names (e.g. SYSTEM) then use the
   * sequence specified in that enumeration.
   */
  // DEFAULT must be declared last so that you can specify enum names
  // in the system property.
  DEFAULT(getDefaultLineSeparator());


  private static String getDefaultLineSeparator() {
    // Android has some unique ordering requirements in this bootstrap process.
    // also, Android will not have the system property set, so we can exit with the null.
    final String prop = SystemProperty.get(JDOMConstants.JDOM2_PROPERTY_LINE_SEPARATOR, "DEFAULT");
    if ("DEFAULT".equals(prop)) {
      // need to do this to catch the normal process where the property is not set
      // which will cause the value 'DEFAULT' to be returned by the getProperty(),
      // or in an unlikely instance when someone sets
      // -Dorg.jdom.output.LineSeparator=DEFAULT
      // which would create some sort of loop to happen....
      return "\r\n";
    }
    else if ("SYSTEM".equals(prop)) {
      return System.getProperty("line.separator");
    }
    else if ("CRNL".equals(prop)) {
      return "\r\n";
    }
    else if ("NL".equals(prop)) {
      return "\n";
    }
    else if ("CR".equals(prop)) {
      return "\r";
    }
    else if ("DOS".equals(prop)) {
      return "\r\n";
    }
    else if ("UNIX".equals(prop)) {
      return "\n";
    }
    else if ("NONE".equals(prop)) {
      return null;
    }
    return prop;
  }


  private final String value;

  LineSeparator(String value) {
    this.value = value;
  }

  /**
   * The String sequence used for this Separator
   *
   * @return an End-Of-Line String
   */
  public String value() {
    return value;
  }
}

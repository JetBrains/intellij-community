/*-- 

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom.output.support;

import org.jdom.CDATA;
import org.jdom.Content;
import org.jdom.EntityRef;
import org.jdom.Text;

import java.util.NoSuchElementException;

/**
 * A model for walking the (potentially formatted) content of an Element.
 * <p>
 * Implementations of this class restructure the content to a particular format
 * and expose the restructured content in this 'Walker' which is a loose
 * equivalent to an iterator.
 * <p>
 * The next() method will return a Content instance (perhaps null) if there
 * is more content. If the returned content is null, then there will be some
 * formatted characters available in the text() method. These characters may
 * need to be represented as CDATA (check the isCDATA() method).
 * <p>
 * Not all CDATA and Text nodes need to be reformatted, and as a result they
 * may be returned as their original CDATA or Text instances instead of using
 * the formatted text() / isCDATA() mechanism.
 * <p>
 * The 'Rules' for the walkers are that no padding is done before the
 * first content step, and no padding is done after the last content step (but
 * the first/last content items may be trimmed to the correct format).
 * Any required padding will be done in plain text (not CDATA) content.
 * Consecutive CDATA sections may be separated by whitespace text for example.
 *
 * @author Rolf Lear
 */
public interface Walker {

  /**
   * If all the content in this walker is empty, or if whatever content
   * is available is Text-like.
   * <p>
   * Text-like content is considered to be {@link Text}, {@link CDATA},
   * {@link EntityRef}, or any (potentially mixed) sequence of these types,
   * but no other types.
   *
   * @return true if there is no content, or all content is Text
   */
  boolean isAllText();

  /**
   * If all the content is Text-like ({@link #isAllText()} returns true), and
   * additionally that any content is either Text or CDATA, and that the
   * values of these Text/CDATA members are all XML Whitespace.
   *
   * @return true
   */
  boolean isAllWhitespace();

  /**
   * Behaves similarly to a regular Iterator
   *
   * @return true if there is more content to be processed
   */
  boolean hasNext();

  /**
   * Similar to an Iterator, but null return values need special treatment.
   *
   * @return the next content to be processed, perhaps null if the next
   * content is re-formatted text of some sort (Text / CDATA).
   * @throws NoSuchElementException if there is no further content.
   */
  Content next();

  /**
   * If the previous call to next() returned null, then this will return the
   * required text to be processed. Check to see whether this text is CDATA
   * by calling the isCDATA() method.
   *
   * @return The current text value (null if the previous invocation of next()
   * returned a non-null value).
   * @throws IllegalStateException if there was not previous call to next()
   */
  String text();

  /**
   * If the previous next() method returned null, then this will indicate
   * whether the current text() value is CDATA or regular Text.
   *
   * @return true if the current text() is valid, and is CDATA.
   * @throws IllegalStateException if there was not previous call to next()
   */
  boolean isCDATA();
}

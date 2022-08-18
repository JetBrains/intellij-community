/*-- 

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom.filter2;

import java.util.List;


/**
 * A generalized filter to restrict visibility or mutability on a list.
 *
 * @param <T> The Generic type of content returned by this Filter
 * @author Jools Enticknap
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
public interface Filter<T> {
  /**
   * Filter the input list keeping only the items that match the Filter.
   *
   * @param content The content to filter.
   * @return a new read-only RandomAccess list of the filtered input content.
   */
  List<T> filter(List<?> content);

  /**
   * Check to see if the content matches this Filter.
   * If it does, return the content cast as this filter's return type,
   * otherwise return null.
   *
   * @param content The content to test.
   * @return The content if it matches the filter, cast as this Filter's type.
   */
  T filter(Object content);

  /**
   * Check to see if the object matches a predefined set of rules.
   *
   * @param content The object to verify.
   * @return <code>true</code> if the object matches a predfined
   * set of rules.
   */
  boolean matches(Object content);

  /**
   * Creates an 'inverse' filter
   *
   * @return a Filter that returns all content except what this Filter
   * instance would.
   */
  Filter<?> negate();

  /**
   * Creates an ORing filter
   *
   * @param filter a second Filter to OR with.
   * @return a new Filter instance that returns the 'union' of this filter and
   * the specified filter.
   */
  Filter<?> or(Filter<?> filter);

  /**
   * Creates an ANDing filter. The generic type of the result is the same as
   * this Filter.
   *
   * @param filter a second Filter to AND with.
   * @return a new Filter instance that returns the 'intersection' of this
   * filter and the specified filter.
   */
  Filter<T> and(Filter<?> filter);

  /**
   * This is similar to the and(Filter) method except the generic type is
   * different.
   *
   * @param <R>    The Generic type of the returned data is taken from the input
   *               instance.
   * @param filter The filter to refine our results with.
   * @return A Filter that requires content to both match our instance and the
   * refining instance, but the generic type of the retuned data is based
   * on the refining instance, not this instance.
   */
  <R> Filter<R> refine(Filter<R> filter);
}

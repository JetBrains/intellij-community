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


/**
 * Allow two filters to be chained together with a logical
 * <b>and</b> operation.
 *
 * @param <T> The Generic type of content returned by this Filter
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
final class AndFilter<T> extends AbstractFilter<T> {

  /**
   * JDOM2 Serialization: Default mechanism
   */
  private static final long serialVersionUID = 200L;

  private final Filter<?> base;
  private final Filter<T> refiner;

  AndFilter(Filter<?> base, Filter<T> refiner) {
    if (base == null || refiner == null) {
      throw new NullPointerException("Cannot have a null base or refiner filter");
    }
    this.base = base;
    this.refiner = refiner;
  }

  @Override
  public T filter(Object content) {
    Object o = base.filter(content);
    if (o != null) {
      return refiner.filter(content);
    }
    return null;
  }

  @Override
  public int hashCode() {
    return base.hashCode() ^ refiner.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof AndFilter<?>) {
      AndFilter<?> them = (AndFilter<?>)obj;
      return (base.equals(them.base) && refiner.equals(them.refiner)) ||
             (refiner.equals(them.base) && base.equals(them.refiner));
    }

    return false;
  }

  @Override
  public String toString() {
    return "[AndFilter: " +
           base +
           ",\n" +
           "            " +
           refiner +
           "]";
  }
}
